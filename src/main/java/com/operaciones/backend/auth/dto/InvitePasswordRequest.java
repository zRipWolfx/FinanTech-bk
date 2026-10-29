package com.operaciones.backend.auth.dto;

public record InvitePasswordRequest(
        String token,
        String newPassword) {
}
