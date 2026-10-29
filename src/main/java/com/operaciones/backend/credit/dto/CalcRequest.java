package com.operaciones.backend.credit.dto;

public record CalcRequest(String monto, Integer cuotas, String frecuenciaPago, Long tasaParamId) {
}
