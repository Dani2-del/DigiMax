package com.Servicio.Acceso.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.Servicio.Acceso.model.AdminAuditEvent;
import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.AdminAuditEventRepository;
import com.Servicio.Acceso.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class GlobalAdminControllerTest {

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private AdminAuditEventRepository auditEvents;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private RedirectAttributes redirectAttributes;

    private GlobalAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new GlobalAdminController(usuarios, auditEvents, mongoTemplate);
    }

    @Test
    void listsUsersNewestFirstByDefault() {
        when(mongoTemplate.count(any(Query.class), org.mockito.ArgumentMatchers.eq(Usuario.class)))
                .thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), org.mockito.ArgumentMatchers.eq(Usuario.class)))
                .thenReturn(List.of(new Usuario("nuevo@example.com", "password-hash", "Nuevo")));
        var model = new org.springframework.ui.ExtendedModelMap();

        String response = controller.usuarios(0, "", "", "", "", model);

        assertThat(response).isEqualTo("admin/global-usuarios");
        assertThat(model.getAttribute("totalElements")).isEqualTo(1L);
        var queryCaptor = org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(Usuario.class));
        assertThat(queryCaptor.getValue().getSortObject())
                .containsEntry("fechaRegistro", -1)
                .containsEntry("id", -1);
    }

    @Test
    void appliesSearchRoleStatusAndVerificationFilters() {
        when(mongoTemplate.count(any(Query.class), org.mockito.ArgumentMatchers.eq(Usuario.class)))
                .thenReturn(0L);
        var model = new org.springframework.ui.ExtendedModelMap();

        controller.usuarios(0, " Ana ", Usuario.ROLE_CONDUCTOR, "false", "true", model);

        var queryCaptor = org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).count(queryCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(Usuario.class));
        assertThat(queryCaptor.getValue().getQueryObject().toJson())
                .contains("Ana")
                .contains(Usuario.ROLE_CONDUCTOR)
                .contains("activo")
                .contains("verificado");
        assertThat(model.getAttribute("selectedRole")).isEqualTo(Usuario.ROLE_CONDUCTOR);
        assertThat(model.getAttribute("selectedActive")).isEqualTo("false");
        assertThat(model.getAttribute("selectedVerified")).isEqualTo("true");
    }

    @Test
    void rendersDashboardWithoutWaitingForExternalTransactionDatabases() {
        when(usuarios.count()).thenReturn(2L);
        when(usuarios.countByActivoTrue()).thenReturn(2L);
        when(usuarios.countByVerificadoFalse()).thenReturn(0L);
        when(usuarios.countByRole(Usuario.ROLE_ADMIN)).thenReturn(1L);
        when(usuarios.countByRole(Usuario.ROLE_MODERATOR)).thenReturn(0L);
        when(auditEvents.findTop8ByOrderByCreatedAtDesc()).thenReturn(List.of());
        var administrator = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority(Usuario.ROLE_ADMIN)));
        var model = new org.springframework.ui.ExtendedModelMap();

        String response = controller.dashboard(model, administrator);

        assertThat(response).isEqualTo("admin/global-dashboard");
        assertThat(model.getAttribute("totalUsuarios")).isEqualTo(2L);
        assertThat(model.containsAttribute("totalTransaccionesGlobales")).isFalse();
    }

    @Test
    void refusesToUpdateUserWithoutBothConfirmations() {
        Usuario target = new Usuario("persona@example.com", "password-hash", "Persona");
        target.setId("user-1");
        when(usuarios.findById("user-1")).thenReturn(Optional.of(target));
        var administrator = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority(Usuario.ROLE_ADMIN)));

        String response = controller.actualizarUsuario(
                "user-1", "Persona nueva", "persona.nueva@example.com", Usuario.ROLE_USER,
                "", true, true, false, "Corrección solicitada", administrator, redirectAttributes);

        assertThat(response).isEqualTo("redirect:/admin/global/usuarios");
        assertThat(target.getNombre()).isEqualTo("Persona");
        verify(usuarios, never()).save(any(Usuario.class));
        verify(auditEvents, never()).save(any(AdminAuditEvent.class));
    }

    @Test
    void predictiveModelPageShowsPlaceholderView() {
        assertThat(controller.modeloPredictivo()).isEqualTo("admin/global-modelo-predictivo");
    }

    @Test
    void recordsPreviousAndUpdatedUserStateInAuditEvent() {
        Usuario target = new Usuario("persona@example.com", "password-hash", "Persona");
        target.setId("user-1");
        when(usuarios.findById("user-1")).thenReturn(Optional.of(target));
        when(usuarios.findByCorreoIgnoreCase("persona.nueva@example.com")).thenReturn(Optional.empty());
        when(usuarios.save(target)).thenReturn(target);
        when(usuarios.findByCorreoIgnoreCase("admin@example.com")).thenReturn(Optional.empty());
        var administrator = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority(Usuario.ROLE_ADMIN)));

        String response = controller.actualizarUsuario(
                "user-1", "Persona nueva", " Persona.Nueva@Example.com ", Usuario.ROLE_USER,
                "", true, true, true, "Corrección solicitada", administrator, redirectAttributes);

        assertThat(response).isEqualTo("redirect:/admin/global/usuarios");
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(auditEvents).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getDetails())
                .contains("Antes: nombre=Persona, correo=persona@example.com")
                .contains("Después: nombre=Persona nueva, correo=persona.nueva@example.com")
                .contains("Motivo: Corrección solicitada");
    }

    @Test
    void assignsModeratorToSelectedServiceAndIncludesItInAudit() {
        Usuario target = new Usuario("persona@example.com", "password-hash", "Persona");
        target.setId("user-1");
        when(usuarios.findById("user-1")).thenReturn(Optional.of(target));
        when(usuarios.findByCorreoIgnoreCase("persona@example.com")).thenReturn(Optional.of(target));
        when(usuarios.save(target)).thenReturn(target);
        when(usuarios.findByCorreoIgnoreCase("admin@example.com")).thenReturn(Optional.empty());
        var administrator = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority(Usuario.ROLE_ADMIN)));

        controller.actualizarUsuario(
                "user-1", "Persona", "persona@example.com", Usuario.ROLE_MODERATOR,
                "TRANSMILENIO", true, true, true, "Asignación de servicio", administrator, redirectAttributes);

        assertThat(target.getServicioAsignado()).isEqualTo("TRANSMILENIO");
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(auditEvents).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getDetails()).contains("servicio=TRANSMILENIO");
    }

    @Test
    void rejectsModeratorWithoutAServiceAssignment() {
        Usuario target = new Usuario("persona@example.com", "password-hash", "Persona");
        target.setId("user-1");
        when(usuarios.findById("user-1")).thenReturn(Optional.of(target));
        var administrator = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority(Usuario.ROLE_ADMIN)));

        controller.actualizarUsuario(
                "user-1", "Persona", "persona@example.com", Usuario.ROLE_MODERATOR,
                "", true, true, true, "Asignación de servicio", administrator, redirectAttributes);

        verify(usuarios, never()).save(any(Usuario.class));
        verify(auditEvents, never()).save(any(AdminAuditEvent.class));
    }
}
