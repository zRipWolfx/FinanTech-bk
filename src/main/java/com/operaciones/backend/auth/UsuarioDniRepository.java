package com.operaciones.backend.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioDniRepository extends JpaRepository<UsuarioDni, Long> {
    Optional<UsuarioDni> findByDni(String dni);
    boolean existsByDni(String dni);
    Optional<UsuarioDni> findByInviteToken(String inviteToken);
    Optional<UsuarioDni> findByCodigoCorreo(String codigoCorreo);
    boolean existsByCorreoIgnoreCase(String correo);
}
