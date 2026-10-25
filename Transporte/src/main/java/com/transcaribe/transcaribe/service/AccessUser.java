package com.transcaribe.transcaribe.service;

import java.time.LocalDateTime;

import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;

public record AccessUser(
        String id,
        String name,
        String email,
        String role,
        boolean active,
        boolean verified,
        LocalDateTime registeredAt,
        String phone,
        String serviceAssignment) {

    public boolean isTransportModerator() {
        return Usuario.ROLE_MODERATOR.equals(role)
                && SistemaTransporte.disponibles().stream()
                        .anyMatch(system -> system.name().equals(serviceAssignment));
    }
}
