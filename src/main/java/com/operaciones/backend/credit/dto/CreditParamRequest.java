package com.operaciones.backend.credit.dto;

public record CreditParamRequest(
        String codigo,
        String nombre,
        String descripcion,
        String kind,
        String periodo,
        String valor,
        String plazosPermitidos,
        Boolean vigente) {
}
