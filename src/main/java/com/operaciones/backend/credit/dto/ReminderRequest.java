package com.operaciones.backend.credit.dto;

public record ReminderRequest(
        Long loanId,
        Long installmentId,
        String email,
        String mensaje) {
}
