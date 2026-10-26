package com.Servicio.Billetera.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "verificaciones_tarjeta")
public class VerificacionTarjeta {

    @Id
    private String usuarioId;

    private AccionVerificacionTarjeta accion;
    private String codigoHash;
    private String salt;
    private LocalDateTime solicitadaEn;
    private LocalDateTime expiraEn;
    private int intentosFallidos;
    private boolean consumida;

    @Version
    private Long version;

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public AccionVerificacionTarjeta getAccion() { return accion; }
    public void setAccion(AccionVerificacionTarjeta accion) { this.accion = accion; }
    public String getCodigoHash() { return codigoHash; }
    public void setCodigoHash(String codigoHash) { this.codigoHash = codigoHash; }
    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
    public LocalDateTime getSolicitadaEn() { return solicitadaEn; }
    public void setSolicitadaEn(LocalDateTime solicitadaEn) { this.solicitadaEn = solicitadaEn; }
    public LocalDateTime getExpiraEn() { return expiraEn; }
    public void setExpiraEn(LocalDateTime expiraEn) { this.expiraEn = expiraEn; }
    public int getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(int intentosFallidos) { this.intentosFallidos = intentosFallidos; }
    public boolean isConsumida() { return consumida; }
    public void setConsumida(boolean consumida) { this.consumida = consumida; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
