package com.operaciones.backend.credit;

import java.math.BigDecimal;
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
@Table(name = "loan_payments")
public class LoanPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(nullable = false, length = 8)
    private String dni;

    @Column(name = "codigo_voucher", nullable = false, unique = true, length = 30)
    private String codigoVoucher;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, length = 30)
    private String medio;

    @Column(name = "detalle_medio", length = 255)
    private String detalleMedio;

    @Column(name = "cuotas_cubiertas", length = 500)
    private String cuotasCubiertas;

    @Lob
    @Column(name = "voucher_html", columnDefinition = "LONGTEXT")
    private String voucherHtml;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @PrePersist
    void onCreate() {
        creadoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getLoanId() {
        return loanId;
    }

    public void setLoanId(Long loanId) {
        this.loanId = loanId;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getCodigoVoucher() {
        return codigoVoucher;
    }

    public void setCodigoVoucher(String codigoVoucher) {
        this.codigoVoucher = codigoVoucher;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getMedio() {
        return medio;
    }

    public void setMedio(String medio) {
        this.medio = medio;
    }

    public String getDetalleMedio() {
        return detalleMedio;
    }

    public void setDetalleMedio(String detalleMedio) {
        this.detalleMedio = detalleMedio;
    }

    public String getCuotasCubiertas() {
        return cuotasCubiertas;
    }

    public void setCuotasCubiertas(String cuotasCubiertas) {
        this.cuotasCubiertas = cuotasCubiertas;
    }

    public String getVoucherHtml() {
        return voucherHtml;
    }

    public void setVoucherHtml(String voucherHtml) {
        this.voucherHtml = voucherHtml;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
