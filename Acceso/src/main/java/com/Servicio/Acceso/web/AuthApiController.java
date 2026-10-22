package com.Servicio.Acceso.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.UsuarioRepository;
import com.Servicio.Acceso.security.JwtService;

import io.jsonwebtoken.JwtException;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final UsuarioRepository usuarios;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;

    public AuthApiController(UsuarioRepository usuarios, UserDetailsService userDetailsService, JwtService jwtService) {
        this.usuarios = usuarios;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUserResponse> me(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return unauthorized();
        }

        try {
            String token = authorization.substring(7);
            UserDetails principal = userDetailsService.loadUserByUsername(jwtService.extractUsername(token));
            if (!principal.isEnabled() || !principal.isAccountNonLocked()
                    || !jwtService.isTokenValid(token, principal)) {
                return unauthorized();
            }
            Usuario usuario = usuarios.findByCorreoIgnoreCase(principal.getUsername()).orElse(null);
            if (usuario == null) {
                return unauthorized();
            }
            return authenticatedUser(usuario);
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException exception) {
            return unauthorized();
        }
    }

    private ResponseEntity<AuthenticatedUserResponse> authenticatedUser(Usuario usuario) {
        String role = usuario.getRole() == null ? Usuario.ROLE_USER : usuario.getRole();
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new AuthenticatedUserResponse(
                        usuario.getId(), usuario.getCorreo(), usuario.getNombre(), role));
    }

    private ResponseEntity<AuthenticatedUserResponse> unauthorized() {
        return ResponseEntity.status(401)
                .cacheControl(CacheControl.noStore())
                .build();
    }

    public record AuthenticatedUserResponse(String id, String email, String name, String role) {
    }
}
