package com.transcaribe.transcaribe.Config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;

class AdminSistemaInterceptorTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final AdminSistemaInterceptor interceptor = new AdminSistemaInterceptor(usuarios);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void moderatorAlwaysUsesAssignedEntityInsteadOfAChangedSessionEntity() throws Exception {
        Usuario moderator = new Usuario("moderator@example.com", "", "Moderator");
        moderator.setRole(Usuario.ROLE_MODERATOR);
        moderator.setSistemaAsignado(SistemaTransporte.TRANSMETRO);
        when(usuarios.findByCorreo("moderator@example.com")).thenReturn(Optional.of(moderator));
        authenticate("moderator@example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin/dashboard");
        request.getSession().setAttribute(
                SistemaTransporte.SESSION_ATTRIBUTE, SistemaTransporte.TRANSCARIBE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(request.getSession().getAttribute(SistemaTransporte.SESSION_ATTRIBUTE))
                .isEqualTo(SistemaTransporte.TRANSMETRO);
    }

    @Test
    void moderatorWithoutAssignedEntityIsDenied() throws Exception {
        Usuario moderator = new Usuario("moderator@example.com", "", "Moderator");
        moderator.setRole(Usuario.ROLE_MODERATOR);
        when(usuarios.findByCorreo("moderator@example.com")).thenReturn(Optional.of(moderator));
        authenticate("moderator@example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void administratorCanUseAnySelectedEntity() throws Exception {
        Usuario administrator = new Usuario("admin@example.com", "", "Admin");
        administrator.setRole(Usuario.ROLE_ADMIN);
        when(usuarios.findByCorreo("admin@example.com")).thenReturn(Optional.of(administrator));
        authenticate("admin@example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/admin/dashboard");
        request.getSession().setAttribute(
                SistemaTransporte.SESSION_ATTRIBUTE, SistemaTransporte.TRANSMETRO);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(request.getSession().getAttribute(SistemaTransporte.SESSION_ATTRIBUTE))
                .isEqualTo(SistemaTransporte.TRANSMETRO);
    }

    private static void authenticate(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, java.util.List.of()));
    }
}
