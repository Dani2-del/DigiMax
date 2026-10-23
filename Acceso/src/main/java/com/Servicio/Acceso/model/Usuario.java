package com.Servicio.Acceso.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

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

    @Field("activo")
    private boolean activo = true;

    @Field("codigo_verificacion")
    private String codigoVerificacion;

    @Field("codigo_verificacion_expira")
    private LocalDateTime codigoVerificacionExpira;

    @Field("verificado")
    private boolean verificado = false;

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

}
