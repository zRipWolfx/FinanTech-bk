package com.operaciones.backend.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioEmpresaRepository extends JpaRepository<UsuarioEmpresa, Long> {
    Optional<UsuarioEmpresa> findByCorreoIgnoreCase(String correo);
    boolean existsByCorreoIgnoreCase(String correo);
    Optional<UsuarioEmpresa> findByCodigoCorreo(String codigoCorreo);
    Optional<UsuarioEmpresa> findByInviteToken(String inviteToken);
}
