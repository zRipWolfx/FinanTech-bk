package com.operaciones.backend.credit.dto;

import java.util.List;

public record CreditParamResponse(
        Long id,
        String codigo,
        String nombre,
        String descripcion,
        String kind,
        String periodo,
        String valor,
        String plazosPermitidos,
        List<Integer> plazos,
        boolean vigente) {
}
