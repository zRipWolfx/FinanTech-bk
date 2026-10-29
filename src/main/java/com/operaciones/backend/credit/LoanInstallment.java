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
import jakarta.persistence.Table;

@Entity
@Table(name = "loan_installments")
public class LoanInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(nullable = false)
    private int numero;

    @Column(name = "fecha_vence", nullable = false)
    private LocalDate fechaVence;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal capital;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal interes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallmentStatus estado = InstallmentStatus.PENDIENTE;

    @Column(name = "pagado_en")
    private Instant pagadoEn;

    public Long getId() {
        return id;
    }

    public Long getLoanId() {
        return loanId;
    }

    public void setLoanId(Long loanId) {
        this.loanId = loanId;
    }

    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public LocalDate getFechaVence() {
        return fechaVence;
    }

    public void setFechaVence(LocalDate fechaVence) {
        this.fechaVence = fechaVence;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public BigDecimal getCapital() {
        return capital;
    }

    public void setCapital(BigDecimal capital) {
        this.capital = capital;
    }

    public BigDecimal getInteres() {
        return interes;
    }

    public void setInteres(BigDecimal interes) {
        this.interes = interes;
    }

    public InstallmentStatus getEstado() {
        return estado;
    }

    public void setEstado(InstallmentStatus estado) {
        this.estado = estado;
    }

    public Instant getPagadoEn() {
        return pagadoEn;
    }

    public void setPagadoEn(Instant pagadoEn) {
        this.pagadoEn = pagadoEn;
    }
}
