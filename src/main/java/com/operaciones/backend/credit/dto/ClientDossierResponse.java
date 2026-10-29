package com.operaciones.backend.credit.dto;

import java.util.List;

public record ClientDossierResponse(
        String dni,
        String nombres,
        String telefono,
        boolean activo,
        String bloqueadoHasta,
        String motivoBloqueo,
        int prestamosTotales,
        int prestamosVigentes,
        int pagosTardios,
        ActiveLoanResponse prestamoVigente,
        List<ActiveLoanResponse> historialPrestamos,
        List<PaymentResponse> historialPagos,
        List<LoanRequestResponse> solicitudes) {
}
