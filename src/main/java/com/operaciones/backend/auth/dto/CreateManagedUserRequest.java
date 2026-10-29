package com.operaciones.backend.auth.dto;

public record CreateManagedUserRequest(
        String kind,
        String nombre,
        String correo,
        String dni,
        String role) {
}
