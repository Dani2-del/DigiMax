package com.transcaribe.transcaribe.Controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;

class UsuarioControllerSystemSelectionTest {

    private UsuarioController controller;

    @BeforeEach
    void setUp() {
        controller = new UsuarioController();
        Usuario admin = new Usuario("admin@example.com", "", "Admin");
        admin.setRole(Usuario.ROLE_ADMIN);
        ReflectionTestUtils.setField(controller, "usuarioRepository", new StubUsuarioRepository(admin));

        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorSwitchRequiresAndCompletesBothConfirmations() {
        MockHttpSession session = new MockHttpSession();

        String firstResponse = controller.seleccionarSistema(
                SistemaTransporte.TRANSMETRO, true, session);

        assertThat(firstResponse).isEqualTo("redirect:/menu?confirmarSITM=true");
        assertThat(session.getAttribute("sitmPendienteConfirmacion"))
                .isEqualTo(SistemaTransporte.TRANSMETRO);
        assertThat(session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE)).isNull();

        String finalResponse = controller.confirmarSeleccionSistema(
                SistemaTransporte.TRANSMETRO, true, session);

        assertThat(finalResponse).isEqualTo("redirect:/menu");
        assertThat(session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE))
                .isEqualTo(SistemaTransporte.TRANSMETRO);
        assertThat(session.getAttribute("sitmPendienteConfirmacion")).isNull();
    }

    @Test
    void administratorCannotConfirmAnotherPendingSystem() {
        MockHttpSession session = new MockHttpSession();
        controller.seleccionarSistema(SistemaTransporte.TRANSMETRO, true, session);

        String response = controller.confirmarSeleccionSistema(
                SistemaTransporte.TRANSCARIBE, true, session);

        assertThat(response).isEqualTo("redirect:/menu?error=true");
        assertThat(session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute("sitmPendienteConfirmacion")).isNull();
    }

    private static final class StubUsuarioRepository extends UsuarioRepository {
        private final Usuario admin;

        private StubUsuarioRepository(Usuario admin) {
            super(null, null, null);
            this.admin = admin;
        }

        @Override
        public Optional<Usuario> findByCorreo(String correo) {
            return "admin@example.com".equals(correo) ? Optional.of(admin) : Optional.empty();
        }
    }
}
