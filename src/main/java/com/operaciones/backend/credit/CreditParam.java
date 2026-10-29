package com.operaciones.backend.credit;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "credit_params")
public class CreditParam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 220)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private ParamKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodo", nullable = false, length = 20)
    private RatePeriod periodo;

    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal valor;

    /** Plazos (cuotas) permitidos, separados por coma. Ej: 6,8,12,18,24 */
    @Column(name = "plazos_permitidos", length = 80)
    private String plazosPermitidos;

    @Column(nullable = false)
    private boolean vigente = true;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        creadoEn = now;
        actualizadoEn = now;
    }

    @PreUpdate
    void onUpdate() {
        actualizadoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public ParamKind getKind() {
        return kind;
    }

    public void setKind(ParamKind kind) {
        this.kind = kind;
    }

    public RatePeriod getPeriodo() {
        return periodo;
    }

    public void setPeriodo(RatePeriod periodo) {
        this.periodo = periodo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public String getPlazosPermitidos() {
        return plazosPermitidos;
    }

    public void setPlazosPermitidos(String plazosPermitidos) {
        this.plazosPermitidos = plazosPermitidos;
    }

    public boolean isVigente() {
        return vigente;
    }

    public void setVigente(boolean vigente) {
        this.vigente = vigente;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
