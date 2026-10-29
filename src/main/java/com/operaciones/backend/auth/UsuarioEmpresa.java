package com.operaciones.backend.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios_empresa")
public class UsuarioEmpresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 120)
    private String apodo;

    @Column(nullable = false, unique = true, length = 160)
    private String correo;

    @Column(nullable = false, length = 30)
    private String rol = StaffRole.OPERACIONES;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(length = 30)
    private String telefono;

    @Column(name = "correo_verificado", nullable = false)
    private boolean correoVerificado = false;

    @Column(name = "telefono_verificado", nullable = false)
    private boolean telefonoVerificado = false;

    @Column(name = "codigo_correo", length = 80)
    private String codigoCorreo;

    @Column(name = "codigo_correo_expira")
    private Instant codigoCorreoExpira;

    @Column(name = "codigo_telefono", length = 12)
    private String codigoTelefono;

    @Column(name = "codigo_telefono_expira")
    private Instant codigoTelefonoExpira;

    @Column(name = "invite_token", length = 80)
    private String inviteToken;

    @Column(name = "invite_expira")
    private Instant inviteExpira;

    @Lob
    @Column(name = "foto", columnDefinition = "LONGBLOB")
    private byte[] foto;

    @Column(name = "foto_content_type", length = 80)
    private String fotoContentType;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @PrePersist
    void onCreate() {
        creadoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApodo() {
        return apodo;
    }

    public void setApodo(String apodo) {
        this.apodo = apodo;
    }

    public String getRol() {
        return rol == null || rol.isBlank() ? StaffRole.OPERACIONES : rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    public void setTotpSecret(String totpSecret) {
        this.totpSecret = totpSecret;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    public void setTotpEnabled(boolean totpEnabled) {
        this.totpEnabled = totpEnabled;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public boolean isCorreoVerificado() {
        return correoVerificado;
    }

    public void setCorreoVerificado(boolean correoVerificado) {
        this.correoVerificado = correoVerificado;
    }

    public boolean isTelefonoVerificado() {
        return telefonoVerificado;
    }

    public void setTelefonoVerificado(boolean telefonoVerificado) {
        this.telefonoVerificado = telefonoVerificado;
    }

    public String getCodigoCorreo() {
        return codigoCorreo;
    }

    public void setCodigoCorreo(String codigoCorreo) {
        this.codigoCorreo = codigoCorreo;
    }

    public Instant getCodigoCorreoExpira() {
        return codigoCorreoExpira;
    }

    public void setCodigoCorreoExpira(Instant codigoCorreoExpira) {
        this.codigoCorreoExpira = codigoCorreoExpira;
    }

    public String getCodigoTelefono() {
        return codigoTelefono;
    }

    public void setCodigoTelefono(String codigoTelefono) {
        this.codigoTelefono = codigoTelefono;
    }

    public Instant getCodigoTelefonoExpira() {
        return codigoTelefonoExpira;
    }

    public void setCodigoTelefonoExpira(Instant codigoTelefonoExpira) {
        this.codigoTelefonoExpira = codigoTelefonoExpira;
    }

    public String getInviteToken() {
        return inviteToken;
    }

    public void setInviteToken(String inviteToken) {
        this.inviteToken = inviteToken;
    }

    public Instant getInviteExpira() {
        return inviteExpira;
    }

    public void setInviteExpira(Instant inviteExpira) {
        this.inviteExpira = inviteExpira;
    }

    public byte[] getFoto() {
        return foto;
    }

    public void setFoto(byte[] foto) {
        this.foto = foto;
    }

    public String getFotoContentType() {
        return fotoContentType;
    }

    public void setFotoContentType(String fotoContentType) {
        this.fotoContentType = fotoContentType;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
