package com.transcaribe.transcaribe.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
public class UsuarioSitmApiController {

    private final UsuarioRepository usuarios;

    public UsuarioSitmApiController(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping("/api/usuario/sitm")
    public SitmResponse currentSitm(Authentication authentication) {
        Usuario usuario = authenticatedUser(authentication);
        SistemaTransporte sitm = Usuario.ROLE_MODERATOR.equals(usuario.getRole())
                ? usuario.getSistemaAsignado()
                : usuario.getSitm();
        return new SitmResponse(sitm == null ? null : sitm.name());
    }

    @PostMapping("/api/usuario/sitm")
    public ResponseEntity<SitmResponse> changeSitm(
            Authentication authentication, @Valid @RequestBody SitmChangeRequest request) {
        Usuario usuario = authenticatedUser(authentication);
        if (!Usuario.ROLE_USER.equals(usuario.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo una cuenta de usuario puede cambiar su SITM.");
        }
        usuario.setSitm(request.sitm());
        usuarios.save(usuario);
        return ResponseEntity.ok(new SitmResponse(request.sitm().name()));
    }

    private Usuario authenticatedUser(Authentication authentication) {
        if (authentication == null || !StringUtils.hasText(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return usuarios.findByCorreo(authentication.getName())
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public record SitmChangeRequest(@NotNull SistemaTransporte sitm) {
    }

    public record SitmResponse(String currentSitm) {
    }
}
