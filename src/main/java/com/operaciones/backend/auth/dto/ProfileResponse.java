package com.operaciones.backend.auth.dto;

public record ProfileResponse(
        String kind,
        String displayName,
        String nombre,
        String apodo,
        String empresa,
        String accessType,
        String identifier,
        String correo,
        String telefono,
        String role,
        String roleCode,
        boolean canEditRole,
        boolean emailVerified,
        boolean phoneVerified,
        boolean twoFactorEnabled,
        boolean hasPhoto,
        String photoUrl,
        String direccion,
        String trabajo,
        String ingresosMensuales) {
}
