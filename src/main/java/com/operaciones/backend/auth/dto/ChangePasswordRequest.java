package com.operaciones.backend.auth.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
