package com.operaciones.backend.credit.dto;

import java.util.List;

public record ClientPortalResponse(
        String dni,
        LoanRequestResponse solicitudActiva,
        ActiveLoanResponse prestamoVigente,
        List<ActiveLoanResponse> prestamosAnteriores,
        List<PaymentResponse> pagos,
        ContractResponse contrato,
        boolean puedeSolicitar) {
}
