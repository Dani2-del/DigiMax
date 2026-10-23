package com.Servicio.Billetera.security;

public record AuthenticatedIdentity(String id, String email, String name, String role) {
}
