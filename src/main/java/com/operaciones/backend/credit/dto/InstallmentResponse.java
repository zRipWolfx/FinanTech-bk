package com.operaciones.backend.credit.dto;

public record InstallmentResponse(
        Long id,
        int numero,
        String fechaVence,
        String monto,
        String capital,
        String interes,
        String estado,
        String pagadoEn) {
}
