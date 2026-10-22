package com.Servicio.Acceso.service;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.UsuarioRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;

    public CustomUserDetailsService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarios.findByCorreoIgnoreCase(correo.trim())
                .orElseThrow(() -> new UsernameNotFoundException("No se encontró la cuenta."));
        String role = StringUtils.hasText(usuario.getRole()) ? usuario.getRole() : Usuario.ROLE_USER;
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }

        return User.withUsername(usuario.getCorreo())
                .password(usuario.getPasswordHash() == null ? "" : usuario.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority(role)))
                .disabled(!usuario.isActivo())
                .accountLocked(!usuario.isVerificado())
                .build();
    }
}
