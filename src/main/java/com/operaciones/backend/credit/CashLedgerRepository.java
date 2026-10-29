package com.operaciones.backend.credit;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CashLedgerRepository extends JpaRepository<CashLedger, Long> {
    List<CashLedger> findAllByOrderByCreadoEnDesc();

    Optional<CashLedger> findTopByOrderByIdDesc();
}
