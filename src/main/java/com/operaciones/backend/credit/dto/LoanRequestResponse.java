package com.operaciones.backend.credit.dto;

public record LoanRequestResponse(
        Long id,
        String clienteNombre,
        String nombres,
        String apellidos,
        String dni,
        String direccion,
        String trabajo,
        String ingresosMensuales,
        String monto,
        int cuotas,
        String frecuenciaPago,
        String estado,
        String periodoTasa,
        String valorTasa,
        String tasaMensualUsada,
        String cuotaMensual,
        String interesTotal,
        String totalPagar,
        String creadoEn) {
}
