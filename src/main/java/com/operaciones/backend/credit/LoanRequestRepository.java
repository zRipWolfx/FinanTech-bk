package com.operaciones.backend.credit;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRequestRepository extends JpaRepository<LoanRequest, Long> {
    List<LoanRequest> findAllByOrderByCreadoEnDesc();

    List<LoanRequest> findByDniOrderByCreadoEnDesc(String dni);

    Optional<LoanRequest> findFirstByDniAndEstadoInOrderByCreadoEnDesc(
            String dni, Collection<LoanRequestStatus> estados);

    boolean existsByDniAndEstadoIn(String dni, Collection<LoanRequestStatus> estados);
}
