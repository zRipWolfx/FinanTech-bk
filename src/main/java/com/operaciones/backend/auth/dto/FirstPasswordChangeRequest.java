package com.operaciones.backend.auth.dto;

public record FirstPasswordChangeRequest(String token, String currentPassword, String newPassword) {
}
