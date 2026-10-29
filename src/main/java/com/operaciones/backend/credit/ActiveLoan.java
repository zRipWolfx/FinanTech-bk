package com.operaciones.backend.credit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "active_loans")
public class ActiveLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(name = "request_id", nullable = false, unique = true)
    private Long requestId;

    @Column(nullable = false, length = 8)
    private String dni;

    @Column(name = "cliente_nombre", nullable = false, length = 160)
    private String clienteNombre;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private int cuotas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentFrequency frecuencia = PaymentFrequency.MENSUAL;

    @Column(name = "tasa_periodo", precision = 12, scale = 8)
    private BigDecimal tasaPeriodo;

    @Column(name = "cuota_monto", precision = 14, scale = 2)
    private BigDecimal cuotaMonto;

    @Column(name = "interes_total", precision = 14, scale = 2)
    private BigDecimal interesTotal;

    @Column(name = "total_pagar", precision = 14, scale = 2)
    private BigDecimal totalPagar;

    @Column(name = "saldo", precision = 14, scale = 2)
    private BigDecimal saldo;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(nullable = false, length = 20)
    private String estado = "VIGENTE";

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @PrePersist
    void onCreate() {
        creadoEn = Instant.now();
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

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public int getCuotas() {
        return cuotas;
    }

    public void setCuotas(int cuotas) {
        this.cuotas = cuotas;
    }

    public PaymentFrequency getFrecuencia() {
        return frecuencia;
    }

    public void setFrecuencia(PaymentFrequency frecuencia) {
        this.frecuencia = frecuencia;
    }

    public BigDecimal getTasaPeriodo() {
        return tasaPeriodo;
    }

    public void setTasaPeriodo(BigDecimal tasaPeriodo) {
        this.tasaPeriodo = tasaPeriodo;
    }

    public BigDecimal getCuotaMonto() {
        return cuotaMonto;
    }

    public void setCuotaMonto(BigDecimal cuotaMonto) {
        this.cuotaMonto = cuotaMonto;
    }

    public BigDecimal getInteresTotal() {
        return interesTotal;
    }

    public void setInteresTotal(BigDecimal interesTotal) {
        this.interesTotal = interesTotal;
    }

    public BigDecimal getTotalPagar() {
        return totalPagar;
    }

    public void setTotalPagar(BigDecimal totalPagar) {
        this.totalPagar = totalPagar;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
