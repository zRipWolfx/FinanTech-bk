package com.operaciones.backend.auth.dto;

public record VerificationSendResponse(
        String channel,
        String message,
        String code,
        String verifyPath,
        String verifyUrl,
        boolean mailed,
        long expiresInSeconds) {
}
