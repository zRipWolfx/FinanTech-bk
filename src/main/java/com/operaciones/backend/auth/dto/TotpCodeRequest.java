package com.operaciones.backend.auth.dto;

public record TotpCodeRequest(
        String token,
        String code,
        Boolean rememberDevice) {
}
