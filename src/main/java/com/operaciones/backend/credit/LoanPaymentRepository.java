package com.operaciones.backend.credit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanPaymentRepository extends JpaRepository<LoanPayment, Long> {
    List<LoanPayment> findByDniOrderByCreadoEnDesc(String dni);

    List<LoanPayment> findByLoanIdOrderByCreadoEnDesc(Long loanId);
}
