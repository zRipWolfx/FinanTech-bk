package com.operaciones.backend.credit.dto;

public record CashMovementResponse(
        Long id,
        String tipo,
        String monto,
        String saldo,
        String detalle,
        String refCodigo,
        String dni,
        String creadoEn) {
}
