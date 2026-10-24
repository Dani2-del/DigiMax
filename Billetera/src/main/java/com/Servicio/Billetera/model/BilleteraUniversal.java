package com.Servicio.Billetera.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "billeteras_universales")
public class BilleteraUniversal {

    @Id
    private String usuarioId;

    @Indexed(unique = true)
    @Field("numero_tarjeta")
    private String numeroTarjeta;

    @Field("saldo")
    private BigDecimal saldo = BigDecimal.ZERO;

    @Field("activada_por_usuario")
    private boolean activadaPorUsuario;

    @Field("movimientos")
    private List<MovimientoBilletera> movimientos = new ArrayList<>();

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getNumeroTarjeta() { return numeroTarjeta; }
    public void setNumeroTarjeta(String numeroTarjeta) { this.numeroTarjeta = numeroTarjeta; }

    public BigDecimal getSaldo() { return saldo == null ? BigDecimal.ZERO : saldo; }
    public void setSaldo(BigDecimal saldo) { this.saldo = saldo; }

    public boolean isActivadaPorUsuario() { return activadaPorUsuario; }
    public void setActivadaPorUsuario(boolean activadaPorUsuario) {
        this.activadaPorUsuario = activadaPorUsuario;
    }

    public List<MovimientoBilletera> getMovimientos() {
        return movimientos == null ? List.of() : movimientos;
    }
    public void setMovimientos(List<MovimientoBilletera> movimientos) { this.movimientos = movimientos; }
}
