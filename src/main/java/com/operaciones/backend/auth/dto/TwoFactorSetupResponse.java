package com.operaciones.backend.auth.dto;

public record TwoFactorSetupResponse(
        String otpauthUrl,
        String secret,
        String message) {
}
