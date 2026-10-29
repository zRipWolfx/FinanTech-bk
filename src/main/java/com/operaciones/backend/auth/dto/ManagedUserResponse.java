package com.operaciones.backend.auth.dto;

public record ManagedUserResponse(
        Long id,
        String kind,
        String nombre,
        String apodo,
        String identifier,
        String role,
        String roleCode,
        boolean active,
        boolean mustChangePassword,
        boolean twoFactorEnabled,
        boolean hasPhoto,
        String photoUrl,
        boolean inviteActive,
        String invitePath,
        String inviteExpiresAt) {
}
