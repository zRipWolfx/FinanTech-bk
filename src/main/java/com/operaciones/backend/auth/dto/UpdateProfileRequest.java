package com.operaciones.backend.auth.dto;

public record UpdateProfileRequest(
        String nombre,
        String apodo,
        String telefono,
        String correo,
        String role,
        String direccion,
        String trabajo,
        String ingresosMensuales) {
}
