package com.operaciones.backend.credit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanInstallmentRepository extends JpaRepository<LoanInstallment, Long> {
    List<LoanInstallment> findByLoanIdOrderByNumeroAsc(Long loanId);
}
