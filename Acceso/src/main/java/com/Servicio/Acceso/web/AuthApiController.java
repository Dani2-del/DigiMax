package com.Servicio.Acceso.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

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

    @GetMapping("/administrative-contacts")
    public ResponseEntity<List<AdministrativeContact>> administrativeContacts(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UserDetails principal = validPrincipal(authorization);
        if (principal == null || !mayReadOperationalContacts(principal)) {
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).build();
        }
        List<AdministrativeContact> contacts = usuarios.findAll().stream()
                .filter(usuario -> usuario.isActivo()
                        && (Usuario.ROLE_ADMIN.equals(normalizeRole(usuario.getRole()))
                                || Usuario.ROLE_MODERATOR.equals(normalizeRole(usuario.getRole()))))
                .filter(usuario -> StringUtils.hasText(usuario.getCorreo()))
                .map(usuario -> new AdministrativeContact(
                        usuario.getId(), usuario.getCorreo(), normalizeRole(usuario.getRole())))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(contacts);
    }

    @PostMapping("/transport-identities")
    public ResponseEntity<List<AuthenticatedUserResponse>> transportIdentities(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody List<String> ids) {
        UserDetails principal = validPrincipal(authorization);
        if (principal == null || !mayReadOperationalContacts(principal)) {
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).build();
        }
        if (ids == null || ids.isEmpty() || ids.size() > 200
                || ids.stream().anyMatch(id -> !StringUtils.hasText(id))) {
            return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).build();
        }
        List<AuthenticatedUserResponse> identities = ids.stream()
                .distinct()
                .map(id -> usuarios.findById(id).orElse(null))
                .filter(usuario -> usuario != null)
                .map(this::toAuthenticatedUser)
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(identities);
    }

    private ResponseEntity<AuthenticatedUserResponse> authenticatedUser(Usuario usuario) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toAuthenticatedUser(usuario));
    }

    private AuthenticatedUserResponse toAuthenticatedUser(Usuario usuario) {
        return new AuthenticatedUserResponse(
                usuario.getId(), usuario.getCorreo(), usuario.getNombre(), normalizeRole(usuario.getRole()),
                usuario.isActivo(), usuario.isVerificado(), usuario.getFechaRegistro(), usuario.getTelefono(),
                usuario.getServicioAsignado());
    }

    private UserDetails validPrincipal(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            String token = authorization.substring(7);
            UserDetails principal = userDetailsService.loadUserByUsername(jwtService.extractUsername(token));
            return principal.isEnabled() && principal.isAccountNonLocked()
                    && jwtService.isTokenValid(token, principal) ? principal : null;
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException exception) {
            return null;
        }
    }

    private static boolean mayReadOperationalContacts(UserDetails principal) {
        return principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> Usuario.ROLE_ADMIN.equals(role)
                        || Usuario.ROLE_MODERATOR.equals(role)
                        || Usuario.ROLE_CONDUCTOR.equals(role));
    }

    private static String normalizeRole(String role) {
        String normalized = role == null ? Usuario.ROLE_USER : role;
        return normalized.startsWith("ROLE_") ? normalized : "ROLE_" + normalized;
    }

    private ResponseEntity<AuthenticatedUserResponse> unauthorized() {
        return ResponseEntity.status(401)
                .cacheControl(CacheControl.noStore())
                .build();
    }

    public record AuthenticatedUserResponse(
            String id,
            String email,
            String name,
            String role,
            boolean active,
            boolean verified,
            java.time.LocalDateTime registeredAt,
            String phone,
            String serviceAssignment) {
    }

    public record AdministrativeContact(String id, String email, String role) {
    }
}
