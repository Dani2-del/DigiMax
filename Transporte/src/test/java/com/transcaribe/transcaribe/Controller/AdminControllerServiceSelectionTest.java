package com.transcaribe.transcaribe.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.BusRepository;
import com.transcaribe.transcaribe.Repository.PerfilTransporteRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.service.AccessIdentityClient;
import com.transcaribe.transcaribe.service.AccessUser;
import com.transcaribe.transcaribe.service.ExcelReportService;
import com.transcaribe.transcaribe.service.PlataformaService;

class AdminControllerServiceSelectionTest {

    private UsuarioRepository usuarios;
    private StubAccessIdentityClient acceso;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        acceso = new StubAccessIdentityClient();
        usuarios = new UsuarioRepository(
                mock(PerfilTransporteRepository.class), mock(MongoOperations.class), acceso);
        controller = new AdminController(
                usuarios,
                mock(PlataformaService.class),
                mock(ExcelReportService.class),
                mock(BusRepository.class),
                null,
                acceso);
    }

    @Test
    void firstConfirmationOnlyStagesServiceAndSecondConfirmationEnters() {
        Usuario administrator = new Usuario("admin@example.com", "", "Admin");
        administrator.setRole(Usuario.ROLE_ADMIN);
        register(administrator);
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, java.util.List.of());
        MockHttpSession session = new MockHttpSession();

        String firstResponse = controller.cambiarEntidadAdministrativa(
                SistemaTransporte.TRANSMILENIO, session, true, authentication);

        assertThat(firstResponse).isEqualTo("redirect:/admin/seleccionar-entidad?confirmar=true");
        assertThat(session.getAttribute("sistemaTransporteActual")).isNull();

        String secondResponse = controller.confirmarCambioEntidad(
                SistemaTransporte.TRANSMILENIO, true, session, authentication);

        assertThat(secondResponse).isEqualTo("redirect:/admin/dashboard");
        assertThat(session.getAttribute("sistemaTransporteActual"))
                .isEqualTo(SistemaTransporte.TRANSMILENIO);
        assertThat(session.getAttribute("entidadPendienteConfirmacion")).isNull();
    }

    @Test
    void finalConfirmationMustMatchThePendingService() {
        Usuario administrator = new Usuario("admin@example.com", "", "Admin");
        administrator.setRole(Usuario.ROLE_ADMIN);
        register(administrator);
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, java.util.List.of());
        MockHttpSession session = new MockHttpSession();
        controller.cambiarEntidadAdministrativa(
                SistemaTransporte.TRANSCARIBE, session, true, authentication);

        String response = controller.confirmarCambioEntidad(
                SistemaTransporte.TRANSMETRO, true, session, authentication);

        assertThat(response).isEqualTo("redirect:/admin/seleccionar-entidad?error=true");
        assertThat(session.getAttribute("sistemaTransporteActual")).isNull();
        assertThat(session.getAttribute("entidadPendienteConfirmacion")).isNull();
    }

    @Test
    void moderatorCannotOpenGlobalEntitySelectorOrStageAnEntityChange() {
        Usuario moderator = new Usuario("moderator@example.com", "", "Moderator");
        moderator.setRole(Usuario.ROLE_MODERATOR);
        moderator.setSistemaAsignado(SistemaTransporte.TRANSCARIBE);
        register(moderator);
        var authentication = new UsernamePasswordAuthenticationToken(
                "moderator@example.com", null, java.util.List.of());
        MockHttpSession session = new MockHttpSession();

        assertThat(controller.seleccionarEntidad(
                new org.springframework.ui.ConcurrentModel(), false, session, authentication))
                .isEqualTo("redirect:/error/403");
        assertThat(controller.cambiarEntidadAdministrativa(
                SistemaTransporte.TRANSMETRO, session, true, authentication))
                .isEqualTo("redirect:/admin/seleccionar-entidad?error=true");
        assertThat(session.getAttribute("entidadPendienteConfirmacion")).isNull();
    }

    @Test
    void moderatorCannotConfirmAnotherEntityEvenWithAnAdminPendingValue() {
        Usuario moderator = new Usuario("moderator@example.com", "", "Moderator");
        moderator.setRole(Usuario.ROLE_MODERATOR);
        moderator.setSistemaAsignado(SistemaTransporte.TRANSCARIBE);
        register(moderator);
        var authentication = new UsernamePasswordAuthenticationToken(
                "moderator@example.com", null, java.util.List.of());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("entidadPendienteConfirmacion", SistemaTransporte.TRANSMETRO);

        assertThat(controller.confirmarCambioEntidad(
                SistemaTransporte.TRANSMETRO, true, session, authentication))
                .isEqualTo("redirect:/admin/seleccionar-entidad?error=true");
        assertThat(session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute("entidadPendienteConfirmacion")).isNull();
    }

    @Test
    void administratorCanConfirmEveryAvailableEntity() {
        Usuario administrator = new Usuario("admin@example.com", "", "Admin");
        administrator.setRole(Usuario.ROLE_ADMIN);
        register(administrator);
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, java.util.List.of());

        for (SistemaTransporte sistema : SistemaTransporte.disponibles()) {
            MockHttpSession session = new MockHttpSession();
            assertThat(controller.cambiarEntidadAdministrativa(
                    sistema, session, true, authentication))
                    .isEqualTo("redirect:/admin/seleccionar-entidad?confirmar=true");

            assertThat(controller.confirmarCambioEntidad(
                    sistema, true, session, authentication))
                    .isEqualTo("redirect:/admin/dashboard");
            assertThat(session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE)).isEqualTo(sistema);
        }
    }

    @Test
    void globalAdministratorCanOpenEachOperationalChatDirectly() {
        Usuario administrator = new Usuario("admin@example.com", "", "Admin");
        administrator.setRole(Usuario.ROLE_ADMIN);
        register(administrator);
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, java.util.List.of());

        for (SistemaTransporte sistema : SistemaTransporte.disponibles()) {
            MockHttpSession session = new MockHttpSession();

            assertThat(controller.abrirChatDesdeConsolaGlobal(sistema, authentication, session))
                    .isEqualTo("redirect:/chat");
            assertThat(session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE)).isEqualTo(sistema);
        }
    }

    @Test
    void nonAdminCannotSwitchOperationalChatFromGlobalConsole() {
        Usuario moderator = new Usuario("moderator@example.com", "", "Moderator");
        moderator.setRole(Usuario.ROLE_MODERATOR);
        register(moderator);
        var authentication = new UsernamePasswordAuthenticationToken(
                "moderator@example.com", null, java.util.List.of());
        MockHttpSession session = new MockHttpSession();

        assertThat(controller.abrirChatDesdeConsolaGlobal(
                SistemaTransporte.TRANSMILENIO, authentication, session))
                .isEqualTo("redirect:/error/403");
        assertThat(session.getAttribute(SistemaTransporte.SESSION_ATTRIBUTE)).isNull();
    }

    private void register(Usuario user) {
        String id = user.getId() == null ? user.getCorreo() : user.getId();
        user.setId(id);
        String assignment = user.getSistemaAsignado() == null
                ? null : user.getSistemaAsignado().name();
        acceso.identities.put(user.getCorreo().toLowerCase(Locale.ROOT),
                new AccessUser(id, user.getNombre(), user.getCorreo(), user.getRole(),
                        user.isActivo(), true, null, null, assignment));
    }

    private static final class StubAccessIdentityClient extends AccessIdentityClient {
        private final Map<String, AccessUser> identities = new HashMap<>();

        private StubAccessIdentityClient() {
            super("http://localhost");
        }

        @Override
        public AccessUser findByEmail(String email) {
            return identities.get(email.toLowerCase(Locale.ROOT));
        }
    }
}
