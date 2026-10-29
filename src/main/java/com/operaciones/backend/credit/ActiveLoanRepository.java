package com.operaciones.backend.credit;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ActiveLoanRepository extends JpaRepository<ActiveLoan, Long> {
    Optional<ActiveLoan> findByDniAndEstado(String dni, String estado);

    List<ActiveLoan> findByDniOrderByCreadoEnDesc(String dni);

    List<ActiveLoan> findByEstadoOrderByCreadoEnDesc(String estado);

    List<ActiveLoan> findAllByOrderByCreadoEnDesc();

    Optional<ActiveLoan> findByCodigo(String codigo);

    boolean existsByDniAndEstado(String dni, String estado);
}
