package com.transcaribe.transcaribe.Model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
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

    @Field("operacion_id")
    private String operationId;

    @Field("sistema_transporte")
    private SistemaTransporte sistemaTransporte = SistemaTransporte.TRANSCARIBE;

    @Field("cantidad_pasajes")
    private Integer cantidadPasajes;

    @DBRef
    @Field("usuario")
    private Usuario legacyUsuario;

    @Field("usuario_id")
    private String usuarioId;

    @Field("usuario_correo")
    private String usuarioCorreo;

    @Field("usuario_nombre")
    private String usuarioNombre;

    @Transient
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

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }

    public SistemaTransporte getSistemaTransporte() {
        return sistemaTransporte == null ? SistemaTransporte.TRANSCARIBE : sistemaTransporte;
    }
    public void setSistemaTransporte(SistemaTransporte sistemaTransporte) {
        this.sistemaTransporte = sistemaTransporte;
    }

    public Integer getCantidadPasajes() { return cantidadPasajes; }
    public void setCantidadPasajes(Integer cantidadPasajes) { this.cantidadPasajes = cantidadPasajes; }

    public Usuario getUsuario() {
        if (usuario != null) {
            return usuario;
        }
        if (legacyUsuario != null) {
            return legacyUsuario;
        }
        if (usuarioId == null && usuarioCorreo == null && usuarioNombre == null) {
            return null;
        }
        Usuario snapshot = new Usuario();
        snapshot.setId(usuarioId);
        snapshot.setCorreo(usuarioCorreo);
        snapshot.setNombre(usuarioNombre);
        usuario = snapshot;
        return snapshot;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
        this.legacyUsuario = null;
        if (usuario == null) {
            this.usuarioId = null;
            this.usuarioCorreo = null;
            this.usuarioNombre = null;
            return;
        }
        this.usuarioId = usuario.getId();
        this.usuarioCorreo = usuario.getCorreo();
        this.usuarioNombre = usuario.getNombre();
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(String usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getUsuarioCorreo() {
        return usuarioCorreo;
    }

    public void setUsuarioCorreo(String usuarioCorreo) {
        this.usuarioCorreo = usuarioCorreo;
    }

    public String getUsuarioNombre() {
        return usuarioNombre;
    }

    public void setUsuarioNombre(String usuarioNombre) {
        this.usuarioNombre = usuarioNombre;
    }
}