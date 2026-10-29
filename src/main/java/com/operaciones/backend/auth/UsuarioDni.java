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
@Table(name = "usuarios_dni")
public class UsuarioDni {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombres;

    @Column(length = 120)
    private String apodo;

    @Column(nullable = false, unique = true, length = 8)
    private String dni;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 30)
    private String telefono;

    @Column(length = 160)
    private String correo;

    @Column(name = "correo_verificado", nullable = false)
    private boolean correoVerificado = false;

    @Column(name = "codigo_correo", length = 80)
    private String codigoCorreo;

    @Column(name = "codigo_correo_expira")
    private Instant codigoCorreoExpira;

    @Column(length = 220)
    private String direccion;

    @Column(length = 120)
    private String trabajo;

    @Column(name = "ingresos_mensuales", precision = 14, scale = 2)
    private java.math.BigDecimal ingresosMensuales;

    @Column(name = "telefono_verificado", nullable = false)
    private boolean telefonoVerificado = false;

    @Column(name = "codigo_telefono", length = 12)
    private String codigoTelefono;

    @Column(name = "codigo_telefono_expira")
    private Instant codigoTelefonoExpira;

    @Column(name = "invite_token", length = 80)
    private String inviteToken;

    @Column(name = "invite_expira")
    private Instant inviteExpira;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

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

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApodo() {
        return apodo;
    }

    public void setApodo(String apodo) {
        this.apodo = apodo;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public boolean isCorreoVerificado() {
        return correoVerificado;
    }

    public void setCorreoVerificado(boolean correoVerificado) {
        this.correoVerificado = correoVerificado;
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

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getTrabajo() {
        return trabajo;
    }

    public void setTrabajo(String trabajo) {
        this.trabajo = trabajo;
    }

    public java.math.BigDecimal getIngresosMensuales() {
        return ingresosMensuales;
    }

    public void setIngresosMensuales(java.math.BigDecimal ingresosMensuales) {
        this.ingresosMensuales = ingresosMensuales;
    }

    public boolean isTelefonoVerificado() {
        return telefonoVerificado;
    }

    public void setTelefonoVerificado(boolean telefonoVerificado) {
        this.telefonoVerificado = telefonoVerificado;
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

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
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
