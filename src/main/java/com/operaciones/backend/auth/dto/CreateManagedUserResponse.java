package com.operaciones.backend.auth.dto;

public record CreateManagedUserResponse(
        ManagedUserResponse user,
        String temporaryPassword,
        String invitePath,
        String message) {
}
