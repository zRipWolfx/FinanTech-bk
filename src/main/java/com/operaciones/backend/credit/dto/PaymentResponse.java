package com.operaciones.backend.credit.dto;

public record PaymentResponse(
        Long id,
        Long loanId,
        String codigoVoucher,
        String monto,
        String medio,
        String detalleMedio,
        String cuotasCubiertas,
        String voucherHtml,
        String creadoEn) {
}
