package com.operaciones.backend.auth.dto;

public record EmpresaLoginRequest(
        String correo,
        String password,
        String trustedDeviceToken) {
}
