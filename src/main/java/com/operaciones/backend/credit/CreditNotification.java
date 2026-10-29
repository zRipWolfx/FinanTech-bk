package com.operaciones.backend.credit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "credit_notifications")
public class CreditNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 8)
    private String dni;

    @Column(nullable = false, length = 160)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String mensaje;

    @Column(nullable = false)
    private boolean leida = false;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    @PrePersist
    void onCreate() {
        creadoEn = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public boolean isLeida() {
        return leida;
    }

    public void setLeida(boolean leida) {
        this.leida = leida;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
