package com.Servicio.Acceso.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Tarjeta {

    private String numeroTarjeta;
    private LocalDateTime fechaCreacion;
    private BigDecimal saldo;
    private BigDecimal limiteSaldo;
    private SistemaTransporte sistemaTransporte = SistemaTransporte.TRANSCARIBE;

    public Tarjeta() {
    }

    public Tarjeta(String numeroTarjeta, BigDecimal saldo) {
        this.numeroTarjeta = numeroTarjeta;
        this.saldo = saldo;
        this.fechaCreacion = LocalDateTime.now();
    }

    public Tarjeta(String numeroTarjeta, BigDecimal saldo, BigDecimal limiteSaldo) {
        this.numeroTarjeta = numeroTarjeta;
        this.saldo = saldo;
        this.limiteSaldo = limiteSaldo;
        this.fechaCreacion = LocalDateTime.now();
    }

    public Tarjeta(String numeroTarjeta, BigDecimal saldo, SistemaTransporte sistemaTransporte) {
        this.numeroTarjeta = numeroTarjeta;
        this.saldo = saldo;
        this.sistemaTransporte = sistemaTransporte;
        this.fechaCreacion = LocalDateTime.now();
    }

    public String getNumeroTarjeta() {
        return numeroTarjeta;
    }

    public void setNumeroTarjeta(String numeroTarjeta) {
        this.numeroTarjeta = numeroTarjeta;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public void setSaldo(BigDecimal saldo) {
        this.saldo = saldo;
    }

    public BigDecimal getLimiteSaldo() {
        return limiteSaldo;
    }

    public void setLimiteSaldo(BigDecimal limiteSaldo) {
        this.limiteSaldo = limiteSaldo;
    }

    public SistemaTransporte getSistemaTransporte() {
        return sistemaTransporte == null ? SistemaTransporte.TRANSCARIBE : sistemaTransporte;
    }

    public void setSistemaTransporte(SistemaTransporte sistemaTransporte) {
        this.sistemaTransporte = sistemaTransporte;
    }
}