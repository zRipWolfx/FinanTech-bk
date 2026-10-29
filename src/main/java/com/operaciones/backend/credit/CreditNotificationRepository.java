package com.operaciones.backend.credit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditNotificationRepository extends JpaRepository<CreditNotification, Long> {
    List<CreditNotification> findByDniOrderByCreadoEnDesc(String dni);

    List<CreditNotification> findAllByOrderByCreadoEnDesc();

    long countByDniAndLeidaFalse(String dni);
}
