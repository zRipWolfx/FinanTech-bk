package com.operaciones.backend.auth;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class TotpUtil {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtil() {
    }

    static String generateSecret() {
        byte[] buffer = new byte[20];
        RANDOM.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    static String otpAuthUrl(String issuer, String account, String secret) {
        String label = urlEncode(issuer) + ":" + urlEncode(account);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    static boolean verifyCode(String secret, String code) {
        if (secret == null || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long timeStep = System.currentTimeMillis() / 1000L / 30L;
        for (long offset = -1; offset <= 1; offset++) {
            if (generateCode(secret, timeStep + offset).equals(code)) {
                return true;
            }
        }
        return false;
    }

    private static String generateCode(String secret, long timeStep) {
        try {
            byte[] key = decodeBase32(secret);
            byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo generar código TOTP", exception);
        }
    }

    private static String encodeBase32(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 31));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 31));
        }
        return out.toString();
    }

    private static byte[] decodeBase32(String secret) {
        String normalized = secret.trim().toUpperCase(Locale.ROOT).replace("=", "");
        int bitCount = normalized.length() * 5;
        byte[] out = new byte[bitCount / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int value = BASE32.indexOf(normalized.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("Secreto TOTP inválido");
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
