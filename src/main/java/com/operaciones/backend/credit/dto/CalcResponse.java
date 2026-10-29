package com.operaciones.backend.credit.dto;

import java.util.List;

public record CalcResponse(
        String monto,
        int cuotas,
        String periodoTasa,
        String valorTasa,
        String tasaMensualEquivalente,
        String cuotaMensual,
        String interesTotal,
        String totalPagar,
        String moraDiariaPct,
        List<Integer> plazosPermitidos) {
}
