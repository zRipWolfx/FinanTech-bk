package com.operaciones.backend.credit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.operaciones.backend.auth.AuthTokenStore;
import com.operaciones.backend.credit.dto.CashMovementResponse;
import com.operaciones.backend.credit.dto.CashSummaryResponse;

@Service
public class CashService {

    public static final BigDecimal SEED_BALANCE = new BigDecimal("2000000.00");

    private final CashLedgerRepository ledgerRepository;
    private final AuthTokenStore tokenStore;

    public CashService(CashLedgerRepository ledgerRepository, AuthTokenStore tokenStore) {
        this.ledgerRepository = ledgerRepository;
        this.tokenStore = tokenStore;
    }

    @Transactional
    public void ensureSeed() {
        if (ledgerRepository.count() > 0) {
            return;
        }
        CashLedger open = new CashLedger();
        open.setTipo("APERTURA");
        open.setMonto(SEED_BALANCE);
        open.setSaldo(SEED_BALANCE);
        open.setDetalle("Saldo inicial de caja para desembolsos FinanTech S.A.");
        open.setRefCodigo("CAJA-INI");
        ledgerRepository.save(open);
    }

    public BigDecimal currentBalance() {
        return ledgerRepository.findTopByOrderByIdDesc()
                .map(CashLedger::getSaldo)
                .orElse(SEED_BALANCE);
    }

    @Transactional
    public CashLedger record(String tipo, BigDecimal montoSigned, String detalle, String ref, String dni) {
        BigDecimal saldo = currentBalance().add(montoSigned).setScale(2, RoundingMode.HALF_UP);
        if (saldo.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo de caja insuficiente");
        }
        CashLedger row = new CashLedger();
        row.setTipo(tipo);
        row.setMonto(montoSigned.abs().setScale(2, RoundingMode.HALF_UP));
        row.setSaldo(saldo);
        row.setDetalle(detalle);
        row.setRefCodigo(ref);
        row.setDni(dni);
        return ledgerRepository.save(row);
    }

    public CashSummaryResponse summary(String bearer) {
        requireStaff(bearer);
        ensureSeed();
        List<CashLedger> all = ledgerRepository.findAllByOrderByCreadoEnDesc();
        BigDecimal desembolsado = all.stream()
                .filter(r -> "DESEMBOLSO".equals(r.getTipo()))
                .map(CashLedger::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cobrado = all.stream()
                .filter(r -> "COBRO".equals(r.getTipo()))
                .map(CashLedger::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CashSummaryResponse(
                money(currentBalance()),
                money(desembolsado),
                money(cobrado),
                all.stream().map(this::toRow).toList());
    }

    private CashMovementResponse toRow(CashLedger r) {
        String signed = ("DESEMBOLSO".equals(r.getTipo()) ? "-" : "+") + money(r.getMonto());
        return new CashMovementResponse(
                r.getId(),
                r.getTipo(),
                signed,
                money(r.getSaldo()),
                r.getDetalle(),
                r.getRefCodigo(),
                r.getDni(),
                r.getCreadoEn().toString());
    }

    private void requireStaff(String bearer) {
        var session = tokenStore.findSession(extract(bearer))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida"));
        if (!"empresa".equals(session.kind())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo personal de FinanTech S.A.");
        }
    }

    private static String money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String extract(String header) {
        if (header == null || header.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión inválida");
        }
        String value = header.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        return value;
    }
}
