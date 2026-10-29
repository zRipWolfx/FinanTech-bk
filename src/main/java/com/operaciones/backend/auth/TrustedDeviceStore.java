package com.operaciones.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class TrustedDeviceStore {

    public static final long TRUST_TTL_SECONDS = 60L * 60L * 24L * 7L;

    private record Entry(Long userId, Instant expiresAt) {
    }

    private final Map<String, Entry> devices = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String issue(Long userId) {
        purgeExpired();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        devices.put(hash(rawToken), new Entry(userId, Instant.now().plusSeconds(TRUST_TTL_SECONDS)));
        return rawToken;
    }

    public boolean isTrusted(Long userId, String rawToken) {
        if (userId == null || rawToken == null || rawToken.isBlank()) {
            return false;
        }
        purgeExpired();
        Entry entry = devices.get(hash(rawToken.trim()));
        if (entry == null) {
            return false;
        }
        if (!userId.equals(entry.userId())) {
            return false;
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            devices.remove(hash(rawToken.trim()));
            return false;
        }
        return true;
    }

    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        devices.remove(hash(rawToken.trim()));
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, Entry>> it = devices.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> item = it.next();
            if (item.getValue().expiresAt().isBefore(now)) {
                it.remove();
            }
        }
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    public Optional<Instant> expiresAt(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        Entry entry = devices.get(hash(rawToken.trim()));
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.expiresAt());
    }
}
