package com.operaciones.backend.credit.dto;

public record LoanRequestCreate(
        String clienteNombre,
        String nombres,
        String apellidos,
        String dni,
        String direccion,
        String trabajo,
        String ingresosMensuales,
        String monto,
        Integer cuotas,
        String frecuenciaPago,
        Long tasaParamId) {
}
