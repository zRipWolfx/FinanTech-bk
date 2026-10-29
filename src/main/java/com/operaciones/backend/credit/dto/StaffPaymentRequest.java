package com.operaciones.backend.credit.dto;

public record StaffPaymentRequest(
        Long loanId,
        String dni,
        String modo,
        Long installmentId,
        String medio,
        String detalleMedio) {
}
