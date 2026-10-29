package com.transcaribe.transcaribe.Model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "usuarios")
public class Usuario {

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";
    public static final String ROLE_MODERATOR = "ROLE_MODERATOR";
    public static final String ROLE_CONDUCTOR = "ROLE_CONDUCTOR";

    @Id
    private String id;

    @Field("correo")
    @Indexed(unique = true)
    private String correo;

    @Field("password_hash")
    private String passwordHash;

    @Field("nombre")
    private String nombre;

    @Field("telefono")
    private String telefono;

    @Field("fecha_registro")
    private LocalDateTime fechaRegistro;

    @Field("role")
    private String role = ROLE_USER;

    /** Sistema que puede administrar una cuenta moderadora. */
    @Field("sistema_asignado")
    private SistemaTransporte sistemaAsignado;

    @Transient
    private boolean moderadorExterno;

    @Field("sitm")
    private SistemaTransporte sitm;

    @Field("activo")
    private boolean activo = true;

    @Field("codigo_verificacion")
    private String codigoVerificacion;

    @Field("codigo_verificacion_expira")
    private LocalDateTime codigoVerificacionExpira;

    @Field("verificado")
    private boolean verificado = false;

    @Field("tarjetas")
    private List<Tarjeta> tarjetas = new ArrayList<>();

    @Field("rutas_favoritas")
    private List<String> rutasFavoritas = new ArrayList<>();

    @Field("preferencias_notificacion_rutas")
    private List<PreferenciaRutaNotificacion> preferenciasNotificacionRutas = new ArrayList<>();

    @Field("bus_asignado")
    private String busAsignado;

    @Field("reset_token")
    private String resetToken;

    @Field("token_expiry")
    private LocalDateTime tokenExpiry;

    @Field("pending_password_hash")
    private String pendingPasswordHash;

    public Usuario() {
    }

    public Usuario(String correo, String passwordHash, String nombre) {
        this.correo = correo;
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.fechaRegistro = LocalDateTime.now();
        this.role = ROLE_USER;
        this.activo = true;
        this.verificado = false;
        this.tarjetas = new ArrayList<>();
        this.rutasFavoritas = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public SistemaTransporte getSistemaAsignado() { return sistemaAsignado; }
    public void setSistemaAsignado(SistemaTransporte sistemaAsignado) { this.sistemaAsignado = sistemaAsignado; }
    public boolean isModeradorExterno() { return moderadorExterno; }
    public void setModeradorExterno(boolean moderadorExterno) { this.moderadorExterno = moderadorExterno; }
    public SistemaTransporte getSitm() { return sitm; }
    public void setSitm(SistemaTransporte sitm) { this.sitm = sitm; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public String getCodigoVerificacion() { return codigoVerificacion; }
    public void setCodigoVerificacion(String codigoVerificacion) { this.codigoVerificacion = codigoVerificacion; }

    public LocalDateTime getCodigoVerificacionExpira() { return codigoVerificacionExpira; }
    public void setCodigoVerificacionExpira(LocalDateTime codigoVerificacionExpira) {
        this.codigoVerificacionExpira = codigoVerificacionExpira;
    }

    public boolean isVerificado() { return verificado; }
    public void setVerificado(boolean verificado) { this.verificado = verificado; }

    public String getResetToken() { return resetToken; }
    public void setResetToken(String resetToken) { this.resetToken = resetToken; }

    public LocalDateTime getTokenExpiry() { return tokenExpiry; }
    public void setTokenExpiry(LocalDateTime tokenExpiry) { this.tokenExpiry = tokenExpiry; }

    public String getPendingPasswordHash() { return pendingPasswordHash; }
    public void setPendingPasswordHash(String pendingPasswordHash) {
        this.pendingPasswordHash = pendingPasswordHash;
    }

    public List<Tarjeta> getTarjetas() {
        if (this.tarjetas == null) {
            this.tarjetas = new ArrayList<>();
        }
        return tarjetas;
    }
    public void setTarjetas(List<Tarjeta> tarjetas) { this.tarjetas = tarjetas; }

    public void agregarTarjeta(String numero, BigDecimal saldoInicial) {
        agregarTarjeta(numero, saldoInicial, SistemaTransporte.TRANSCARIBE);
    }

    public void agregarTarjeta(String numero, BigDecimal saldoInicial, SistemaTransporte sistemaTransporte) {
        if (this.tarjetas == null) this.tarjetas = new ArrayList<>();
        this.tarjetas.add(new Tarjeta(numero, saldoInicial, sistemaTransporte));
    }

    public void eliminarTarjeta(String numero) {
        if (this.tarjetas != null) {
            this.tarjetas.removeIf(t -> t.getNumeroTarjeta().equals(numero));
        }
    }

    public BigDecimal getSaldoTotal() {
        if (this.tarjetas == null || this.tarjetas.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return tarjetas.stream()
                .map(Tarjeta::getSaldo)
                .filter(s -> s != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<String> getRutasFavoritas() {
        if (this.rutasFavoritas == null) {
            this.rutasFavoritas = new ArrayList<>();
        }
        return rutasFavoritas;
    }
    public void setRutasFavoritas(List<String> rutasFavoritas) { this.rutasFavoritas = rutasFavoritas; }

    public void agregarRutaFavorita(String ruta) {
        if (this.rutasFavoritas == null) this.rutasFavoritas = new ArrayList<>();
        if (!this.rutasFavoritas.contains(ruta)) {
            this.rutasFavoritas.add(ruta);
        }
    }

    public void eliminarRutaFavorita(String ruta) {
        if (this.rutasFavoritas != null) {
            this.rutasFavoritas.remove(ruta);
        }
        getPreferenciasNotificacionRutas().removeIf(preferencia -> ruta.equals(preferencia.getRuta()));
    }

    public List<PreferenciaRutaNotificacion> getPreferenciasNotificacionRutas() {
        if (preferenciasNotificacionRutas == null) {
            preferenciasNotificacionRutas = new ArrayList<>();
        }
        return preferenciasNotificacionRutas;
    }

    public void setPreferenciasNotificacionRutas(
            List<PreferenciaRutaNotificacion> preferenciasNotificacionRutas) {
        this.preferenciasNotificacionRutas = preferenciasNotificacionRutas;
    }

    public PreferenciaRutaNotificacion obtenerPreferenciaNotificacionRuta(String ruta) {
        return getPreferenciasNotificacionRutas().stream()
                .filter(preferencia -> ruta.equals(preferencia.getRuta()))
                .findFirst()
                .orElseGet(() -> new PreferenciaRutaNotificacion(
                        ruta, false, LocalTime.of(6, 0), LocalTime.of(8, 0)));
    }

    public void guardarPreferenciaNotificacionRuta(PreferenciaRutaNotificacion preferencia) {
        getPreferenciasNotificacionRutas().removeIf(actual -> actual.getRuta().equals(preferencia.getRuta()));
        getPreferenciasNotificacionRutas().add(preferencia);
    }

    public String getBusAsignado() { return busAsignado; }
    public void setBusAsignado(String busAsignado) { this.busAsignado = busAsignado; }

}
