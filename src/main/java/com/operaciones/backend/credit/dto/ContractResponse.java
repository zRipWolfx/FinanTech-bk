package com.operaciones.backend.credit.dto;

public record ContractResponse(
        Long id,
        Long loanId,
        String codigo,
        String firmaSimulada,
        String contenidoHtml,
        String creadoEn) {
}
