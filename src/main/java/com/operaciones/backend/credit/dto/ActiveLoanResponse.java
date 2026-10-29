package com.operaciones.backend.credit.dto;

import java.util.List;

public record ActiveLoanResponse(
        Long id,
        String codigo,
        String dni,
        String clienteNombre,
        String monto,
        int cuotas,
        String frecuencia,
        String tasaPeriodoPct,
        String cuotaMonto,
        String interesTotal,
        String totalPagar,
        String saldo,
        String fechaInicio,
        String estado,
        int cuotasPagadas,
        int cuotasPendientes,
        int progresoPct,
        String diaPago,
        String proximoVence,
        boolean enMora,
        List<InstallmentResponse> cronograma) {
}
