package com.Servicio.RecargasMovil.security;

public record AuthenticatedIdentity(String id, String email, String name, String role) {
}
