package com.operaciones.backend.credit.dto;

import java.util.List;

public record CashSummaryResponse(
        String saldoDisponible,
        String totalDesembolsado,
        String totalCobrado,
        List<CashMovementResponse> movimientos) {
}
