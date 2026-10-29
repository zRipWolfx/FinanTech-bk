package com.operaciones.backend.credit.dto;

public record DueInstallmentResponse(
        Long installmentId,
        Long loanId,
        String codigo,
        String dni,
        String clienteNombre,
        int numero,
        String fechaVence,
        String monto,
        String estado,
        int diasAtraso) {
}
