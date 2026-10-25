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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.Servicio.Acceso.model.AdminAuditEvent;
import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.AdminAuditEventRepository;
import com.Servicio.Acceso.repository.UsuarioRepository;
import com.Servicio.Acceso.service.GlobalOperationsService;

@ExtendWith(MockitoExtension.class)
class GlobalAdminControllerTest {

    @Mock
    private UsuarioRepository usuarios;

    @Mock
    private AdminAuditEventRepository auditEvents;

    @Mock
    private GlobalOperationsService globalOperations;

    @Mock
    private RedirectAttributes redirectAttributes;

    private GlobalAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new GlobalAdminController(usuarios, auditEvents, globalOperations);
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
