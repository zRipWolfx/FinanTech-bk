package com.operaciones.backend.auth.dto;

public record AuthResponse(
        String status,
        String token,
        String kind,
        String displayName,
        String otpauthUrl,
        String secret,
        String trustedDeviceToken,
        Boolean emailLoginAvailable) {

    public static AuthResponse ok(String token, String kind, String displayName) {
        return new AuthResponse("OK", token, kind, displayName, null, null, null, null);
    }

    public static AuthResponse ok(String token, String kind, String displayName, String trustedDeviceToken) {
        return new AuthResponse("OK", token, kind, displayName, null, null, trustedDeviceToken, null);
    }

    public static AuthResponse changePassword(String token, String kind, String displayName) {
        return new AuthResponse("CHANGE_PASSWORD", token, kind, displayName, null, null, null, null);
    }

    public static AuthResponse need2fa(String token, String kind, String displayName, boolean emailLoginAvailable) {
        return new AuthResponse("NEED_2FA", token, kind, displayName, null, null, null, emailLoginAvailable);
    }

    public static AuthResponse setup2fa(
            String token,
            String kind,
            String displayName,
            String otpauthUrl,
            String secret) {
        return new AuthResponse("SETUP_2FA", token, kind, displayName, otpauthUrl, secret, null, null);
    }
}
