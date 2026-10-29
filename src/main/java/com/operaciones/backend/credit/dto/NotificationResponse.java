package com.operaciones.backend.credit.dto;

public record NotificationResponse(
        Long id,
        String titulo,
        String mensaje,
        boolean leida,
        String creadoEn) {
}
