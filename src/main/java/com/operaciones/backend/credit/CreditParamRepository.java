package com.operaciones.backend.credit;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditParamRepository extends JpaRepository<CreditParam, Long> {
    Optional<CreditParam> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);

    List<CreditParam> findByKindAndVigenteTrueOrderByIdAsc(ParamKind kind);

    List<CreditParam> findAllByOrderByIdAsc();
}
