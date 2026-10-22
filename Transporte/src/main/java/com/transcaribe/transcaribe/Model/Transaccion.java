package com.transcaribe.transcaribe.Model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Document(collection = "transacciones")
public class Transaccion {

    @Id
    private String id;

    @Field("monto")
    private double monto;

    @CreatedDate
    @Field("fecha")
    private LocalDateTime fecha;

    @Field("tipo")
    private String tipo;

    @Field("metodo_pago")
    private String metodoPago;

    @Field("cuenta_pse")
    private String cuentaPse;

    @Field("tarjeta_transcaribe")
    private String tarjetaTranscaribe;

    @Field("sistema_transporte")
    private SistemaTransporte sistemaTransporte = SistemaTransporte.TRANSCARIBE;

    @Field("cantidad_pasajes")
    private Integer cantidadPasajes;

    @DBRef
    @Field("usuario")
    private Usuario usuario;

    public Transaccion() {}

    public Transaccion(double monto, LocalDateTime fecha, String tipo, Usuario usuario) {
        this.monto = monto;
        this.fecha = fecha;
        this.tipo = tipo;
        this.usuario = usuario;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getMetodoPago() { return metodoPago; }
    public void setMetodoPago(String metodoPago) { this.metodoPago = metodoPago; }

    public String getCuentaPse() { return cuentaPse; }
    public void setCuentaPse(String cuentaPse) { this.cuentaPse = cuentaPse; }

    public String getTarjetaTranscaribe() { return tarjetaTranscaribe; }
    public void setTarjetaTranscaribe(String tarjetaTranscaribe) { this.tarjetaTranscaribe = tarjetaTranscaribe; }

    public SistemaTransporte getSistemaTransporte() {
        return sistemaTransporte == null ? SistemaTransporte.TRANSCARIBE : sistemaTransporte;
    }
    public void setSistemaTransporte(SistemaTransporte sistemaTransporte) {
        this.sistemaTransporte = sistemaTransporte;
    }

    public Integer getCantidadPasajes() { return cantidadPasajes; }
    public void setCantidadPasajes(Integer cantidadPasajes) { this.cantidadPasajes = cantidadPasajes; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
}