package com.Servicio.Lector.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "tarjetas_rfid")
public class TarjetaRfid {

    @Id
    private String uid;

    @Indexed
    private String usuarioId;

    private boolean activa = true;
    private LocalDateTime vinculadaEn = LocalDateTime.now();

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public LocalDateTime getVinculadaEn() { return vinculadaEn; }
    public void setVinculadaEn(LocalDateTime vinculadaEn) { this.vinculadaEn = vinculadaEn; }
    public boolean isDisponible() { return usuarioId == null || usuarioId.isBlank(); }
}
