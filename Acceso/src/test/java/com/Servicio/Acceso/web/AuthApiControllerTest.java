package com.Servicio.Acceso.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.UsuarioRepository;
import com.Servicio.Acceso.security.JwtService;

@ExtendWith(MockitoExtension.class)
class AuthApiControllerTest {

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private JwtService jwtService;

    private AuthApiController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthApiController(usuarios, userDetailsService, jwtService);
    }

    @Test
    void rejectsWebSessionWithoutBearerToken() {
        assertThat(controller.me(null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(userDetailsService, never()).loadUserByUsername("persona@example.com");
        verify(usuarios, never()).findByCorreoIgnoreCase("persona@example.com");
    }

    @Test
    void returnsIdentityForValidBearerToken() {
        UserDetails principal = User.withUsername("persona@example.com")
                .password("")
                .authorities(Usuario.ROLE_USER)
                .build();
        Usuario usuario = new Usuario("persona@example.com", "", "Persona");
        usuario.setId("123");
        when(jwtService.extractUsername("token-valido")).thenReturn("persona@example.com");
        when(userDetailsService.loadUserByUsername("persona@example.com")).thenReturn(principal);
        when(jwtService.isTokenValid("token-valido", principal)).thenReturn(true);
        when(usuarios.findByCorreoIgnoreCase("persona@example.com")).thenReturn(Optional.of(usuario));

        var response = controller.me("Bearer token-valido");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new AuthApiController.AuthenticatedUserResponse(
                "123", "persona@example.com", "Persona", Usuario.ROLE_USER));
    }
}
