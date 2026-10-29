package com.operaciones.backend.credit;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientCreditBlockRepository extends JpaRepository<ClientCreditBlock, Long> {
    Optional<ClientCreditBlock> findByDni(String dni);

    void deleteByDni(String dni);
}
