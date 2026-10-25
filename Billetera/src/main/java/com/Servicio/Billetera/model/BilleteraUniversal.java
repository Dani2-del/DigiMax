package com.Servicio.Billetera.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;

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

    @Field("estado_tarjeta")
    private EstadoTarjeta estado = EstadoTarjeta.ACTIVA;

    @Field("diseno_tarjeta")
    private DisenoTarjeta diseno = DisenoTarjeta.OCEANO;

    @Field("movimientos")
    private List<MovimientoBilletera> movimientos = new ArrayList<>();

    @Field("tarjetas_desactivadas")
    private List<TarjetaDesactivada> tarjetasDesactivadas = new ArrayList<>();

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

    public EstadoTarjeta getEstado() {
        return estado == null ? EstadoTarjeta.ACTIVA : estado;
    }
    public void setEstado(EstadoTarjeta estado) { this.estado = estado; }

    public DisenoTarjeta getDiseno() {
        return diseno == null ? DisenoTarjeta.OCEANO : diseno;
    }
    public void setDiseno(DisenoTarjeta diseno) { this.diseno = diseno; }

    public List<MovimientoBilletera> getMovimientos() {
        return movimientos == null ? List.of() : movimientos;
    }
    public void setMovimientos(List<MovimientoBilletera> movimientos) { this.movimientos = movimientos; }

    public List<TarjetaDesactivada> getTarjetasDesactivadas() {
        return tarjetasDesactivadas == null ? List.of() : tarjetasDesactivadas;
    }
    public void setTarjetasDesactivadas(List<TarjetaDesactivada> tarjetasDesactivadas) {
        this.tarjetasDesactivadas = tarjetasDesactivadas;
    }

    public static class TarjetaDesactivada {
        private String numeroTarjeta;
        private BigDecimal saldoAlDesactivar;
        private DisenoTarjeta diseno;
        private LocalDateTime desactivadaEn;
        private boolean saldoTransferido;

        public String getNumeroTarjeta() { return numeroTarjeta; }
        public void setNumeroTarjeta(String numeroTarjeta) { this.numeroTarjeta = numeroTarjeta; }
        public BigDecimal getSaldoAlDesactivar() { return saldoAlDesactivar; }
        public void setSaldoAlDesactivar(BigDecimal saldoAlDesactivar) {
            this.saldoAlDesactivar = saldoAlDesactivar;
        }
        public DisenoTarjeta getDiseno() {
            return diseno == null ? DisenoTarjeta.OCEANO : diseno;
        }
        public void setDiseno(DisenoTarjeta diseno) { this.diseno = diseno; }
        public LocalDateTime getDesactivadaEn() { return desactivadaEn; }
        public void setDesactivadaEn(LocalDateTime desactivadaEn) { this.desactivadaEn = desactivadaEn; }
        public boolean isSaldoTransferido() { return saldoTransferido; }
        public void setSaldoTransferido(boolean saldoTransferido) { this.saldoTransferido = saldoTransferido; }
    }
}
