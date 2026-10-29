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
@Table(name = "loan_requests")
public class LoanRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_nombre", nullable = false, length = 120)
    private String clienteNombre;

    @Column(length = 80)
    private String nombres;

    @Column(length = 80)
    private String apellidos;

    @Column(nullable = false, length = 8)
    private String dni;

    @Column(length = 220)
    private String direccion;

    @Column(length = 120)
    private String trabajo;

    @Column(name = "ingresos_mensuales", precision = 14, scale = 2)
    private BigDecimal ingresosMensuales;

    @Enumerated(EnumType.STRING)
    @Column(name = "frecuencia_pago", length = 20)
    private PaymentFrequency frecuenciaPago = PaymentFrequency.MENSUAL;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private int cuotas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LoanRequestStatus estado = LoanRequestStatus.EN_EVALUACION;

    @Column(name = "tasa_mensual_usada", precision = 12, scale = 8)
    private BigDecimal tasaMensualUsada;

    @Column(name = "periodo_tasa", length = 20)
    private String periodoTasa;

    @Column(name = "valor_tasa", precision = 12, scale = 6)
    private BigDecimal valorTasa;

    @Column(name = "cuota_mensual", precision = 14, scale = 2)
    private BigDecimal cuotaMensual;

    @Column(name = "interes_total", precision = 14, scale = 2)
    private BigDecimal interesTotal;

    @Column(name = "total_pagar", precision = 14, scale = 2)
    private BigDecimal totalPagar;

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

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
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

    public BigDecimal getIngresosMensuales() {
        return ingresosMensuales;
    }

    public void setIngresosMensuales(BigDecimal ingresosMensuales) {
        this.ingresosMensuales = ingresosMensuales;
    }

    public PaymentFrequency getFrecuenciaPago() {
        return frecuenciaPago;
    }

    public void setFrecuenciaPago(PaymentFrequency frecuenciaPago) {
        this.frecuenciaPago = frecuenciaPago;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
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

    public LoanRequestStatus getEstado() {
        return estado;
    }

    public void setEstado(LoanRequestStatus estado) {
        this.estado = estado;
    }

    public BigDecimal getTasaMensualUsada() {
        return tasaMensualUsada;
    }

    public void setTasaMensualUsada(BigDecimal tasaMensualUsada) {
        this.tasaMensualUsada = tasaMensualUsada;
    }

    public String getPeriodoTasa() {
        return periodoTasa;
    }

    public void setPeriodoTasa(String periodoTasa) {
        this.periodoTasa = periodoTasa;
    }

    public BigDecimal getValorTasa() {
        return valorTasa;
    }

    public void setValorTasa(BigDecimal valorTasa) {
        this.valorTasa = valorTasa;
    }

    public BigDecimal getCuotaMensual() {
        return cuotaMensual;
    }

    public void setCuotaMensual(BigDecimal cuotaMensual) {
        this.cuotaMensual = cuotaMensual;
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

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
