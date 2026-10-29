package com.operaciones.backend.credit.dto;

public record PaymentCreateRequest(
        String modo,
        Long installmentId,
        String medio,
        String detalleMedio) {
}
