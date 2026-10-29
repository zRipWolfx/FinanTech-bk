package com.operaciones.backend.credit;

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
@Table(name = "loan_contracts")
public class LoanContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loan_id", nullable = false, unique = true)
    private Long loanId;

    @Column(nullable = false, length = 8)
    private String dni;

    @Column(name = "codigo", nullable = false, unique = true, length = 30)
    private String codigo;

    @Column(name = "firma_simulada", nullable = false, length = 120)
    private String firmaSimulada;

    @Lob
    @Column(name = "contenido_html", nullable = false, columnDefinition = "LONGTEXT")
    private String contenidoHtml;

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

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getFirmaSimulada() {
        return firmaSimulada;
    }

    public void setFirmaSimulada(String firmaSimulada) {
        this.firmaSimulada = firmaSimulada;
    }

    public String getContenidoHtml() {
        return contenidoHtml;
    }

    public void setContenidoHtml(String contenidoHtml) {
        this.contenidoHtml = contenidoHtml;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
