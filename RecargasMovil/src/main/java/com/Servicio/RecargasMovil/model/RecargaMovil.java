package com.Servicio.RecargasMovil.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "recargas_movil")
public class RecargaMovil {

    public static final String STATUS_PROCESSING = "PROCESANDO";
    public static final String STATUS_SIMULATED = "SIMULADA";
    public static final String STATUS_REJECTED = "RECHAZADA";

    @Id
    private String operacionId;

    private String usuarioId;
    private String lineaId;
    private String telefono;
    private String operador;
    private String paquete;
    private BigDecimal monto;
    private String estado = STATUS_PROCESSING;
    private LocalDateTime fecha = LocalDateTime.now();

    public String getOperacionId() { return operacionId; }
    public void setOperacionId(String operacionId) { this.operacionId = operacionId; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getLineaId() { return lineaId; }
    public void setLineaId(String lineaId) { this.lineaId = lineaId; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getOperador() { return operador; }
    public void setOperador(String operador) { this.operador = operador; }

    public String getPaquete() { return paquete; }
    public void setPaquete(String paquete) { this.paquete = paquete; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
