package com.operaciones.backend.credit;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanContractRepository extends JpaRepository<LoanContract, Long> {
    Optional<LoanContract> findByLoanId(Long loanId);

    Optional<LoanContract> findByDni(String dni);
}
