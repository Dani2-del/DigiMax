package com.Servicio.Acceso.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AdminUsersControllerTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private UsuarioRepository usuarios;

    private AdminUsersController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminUsersController(mongoTemplate, usuarios);
    }

    @Test
    void rejectsInvalidUserListFilters() {
        var response = controller.listUsers(0, 101, "", "", null, null, null, null, "name");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mongoTemplate, never()).count(any(Query.class), eq(Usuario.class));
    }

    @Test
    void rejectsUnknownUserListSortOrder() {
        var response = controller.listUsers(0, 50, "", "", null, null, null, null, "oldest");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mongoTemplate, never()).count(any(Query.class), eq(Usuario.class));
    }

    @Test
    void returnsCentralUserSummariesWithoutCredentials() {
        Usuario user = new Usuario("persona@example.com", "password-hash", "Persona");
        user.setId("user-1");
        when(mongoTemplate.count(any(Query.class), eq(Usuario.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Usuario.class))).thenReturn(List.of(user));

        var response = controller.listUsers(0, 50, "", "", null, null, null, null, "name");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new AdminUsersController.UserPage(
                List.of(new AdminUsersController.UserSummary(
                        "user-1", "Persona", "persona@example.com", Usuario.ROLE_USER,
                        true, false, user.getFechaRegistro(), null, null)),
                1, 1, 0, 50));
        verify(mongoTemplate).find(any(Query.class), eq(Usuario.class));
    }

    @Test
    void returnsCentralUserByIdWithoutCredentials() {
        Usuario user = new Usuario("persona@example.com", "password-hash", "Persona");
        user.setId("user-1");
        when(usuarios.findById("user-1")).thenReturn(Optional.of(user));

        var response = controller.getUser("user-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new AdminUsersController.UserSummary(
                "user-1", "Persona", "persona@example.com", Usuario.ROLE_USER,
                true, false, user.getFechaRegistro(), null, null));
    }

    @Test
    void returnsNotFoundWhenCentralUserDoesNotExist() {
        when(usuarios.findById("missing")).thenReturn(Optional.empty());

        var response = controller.getUser("missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updatesCentralIdentityAndRole() {
        Usuario user = new Usuario("persona@example.com", "password-hash", "Persona");
        user.setId("user-1");
        when(usuarios.findById("user-1")).thenReturn(Optional.of(user));
        when(usuarios.findByCorreoIgnoreCase("persona.nueva@example.com")).thenReturn(Optional.empty());
        when(usuarios.save(user)).thenReturn(user);
        var administrator = User.withUsername("admin@example.com")
                .password("")
                .authorities(Usuario.ROLE_ADMIN)
                .build();

        var response = controller.updateUser("user-1",
                new AdminUsersController.UpdateUserRequest(
                        "Persona nueva", " Persona.Nueva@Example.com ", Usuario.ROLE_CONDUCTOR, true, null),
                administrator);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.getNombre()).isEqualTo("Persona nueva");
        assertThat(user.getCorreo()).isEqualTo("persona.nueva@example.com");
        assertThat(user.getRole()).isEqualTo(Usuario.ROLE_CONDUCTOR);
        verify(usuarios).save(user);
    }

    @Test
    void assignsModeratorToValidService() {
        Usuario user = new Usuario("persona@example.com", "password-hash", "Persona");
        user.setId("user-1");
        when(usuarios.findById("user-1")).thenReturn(Optional.of(user));
        when(usuarios.findByCorreoIgnoreCase("persona@example.com")).thenReturn(Optional.of(user));
        when(usuarios.save(user)).thenReturn(user);
        var administrator = User.withUsername("admin@example.com")
                .password("")
                .authorities(Usuario.ROLE_ADMIN)
                .build();

        var response = controller.updateUser("user-1",
                new AdminUsersController.UpdateUserRequest(
                        "Persona", "persona@example.com", Usuario.ROLE_MODERATOR, true, "TRANSMILENIO"),
                administrator);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.getServicioAsignado()).isEqualTo("TRANSMILENIO");
    }

    @Test
    void transportModeratorCanAssignRoleWithinOwnService() {
        Usuario target = new Usuario("persona@example.com", "password-hash", "Persona");
        target.setId("user-1");
        Usuario moderator = new Usuario("moderador@example.com", "password-hash", "Moderador");
        moderator.setId("moderator-1");
        moderator.setRole(Usuario.ROLE_MODERATOR);
        moderator.setServicioAsignado("TRANSCARIBE");
        when(usuarios.findById("user-1")).thenReturn(Optional.of(target));
        when(usuarios.findByCorreoIgnoreCase("moderador@example.com")).thenReturn(Optional.of(moderator));
        when(usuarios.findByCorreoIgnoreCase("persona@example.com")).thenReturn(Optional.of(target));
        when(usuarios.save(target)).thenReturn(target);
        var authenticatedModerator = User.withUsername("moderador@example.com")
                .password("")
                .authorities(Usuario.ROLE_MODERATOR)
                .build();

        var response = controller.updateUser("user-1",
                new AdminUsersController.UpdateUserRequest(
                        "Persona", "persona@example.com", Usuario.ROLE_MODERATOR, true, "TRANSCARIBE"),
                authenticatedModerator);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(target.getRole()).isEqualTo(Usuario.ROLE_MODERATOR);
        assertThat(target.getServicioAsignado()).isEqualTo("TRANSCARIBE");
        verify(usuarios).save(target);
    }

    @Test
    void transportModeratorCanAssignConductorRoleWithinOwnService() {
        Usuario target = new Usuario("persona@example.com", "password-hash", "Persona");
        target.setId("user-1");
        Usuario moderator = new Usuario("moderador@example.com", "password-hash", "Moderador");
        moderator.setId("moderator-1");
        moderator.setRole(Usuario.ROLE_MODERATOR);
        moderator.setServicioAsignado("TRANSCARIBE");
        when(usuarios.findById("user-1")).thenReturn(Optional.of(target));
        when(usuarios.findByCorreoIgnoreCase("moderador@example.com")).thenReturn(Optional.of(moderator));
        when(usuarios.findByCorreoIgnoreCase("persona@example.com")).thenReturn(Optional.of(target));
        when(usuarios.save(target)).thenReturn(target);
        var authenticatedModerator = User.withUsername("moderador@example.com")
                .password("")
                .authorities(Usuario.ROLE_MODERATOR)
                .build();

        var response = controller.updateUser("user-1",
                new AdminUsersController.UpdateUserRequest(
                        "Persona", "persona@example.com", Usuario.ROLE_CONDUCTOR, true, null),
                authenticatedModerator);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(target.getRole()).isEqualTo(Usuario.ROLE_CONDUCTOR);
        assertThat(target.getServicioAsignado()).isNull();
        verify(usuarios).save(target);
    }

    @Test
    void transportModeratorCannotAssignUserToAnotherService() {
        Usuario target = new Usuario("persona@example.com", "password-hash", "Persona");
        target.setId("user-1");
        Usuario moderator = new Usuario("moderador@example.com", "password-hash", "Moderador");
        moderator.setId("moderator-1");
        moderator.setRole(Usuario.ROLE_MODERATOR);
        moderator.setServicioAsignado("TRANSCARIBE");
        when(usuarios.findById("user-1")).thenReturn(Optional.of(target));
        when(usuarios.findByCorreoIgnoreCase("moderador@example.com")).thenReturn(Optional.of(moderator));
        var authenticatedModerator = User.withUsername("moderador@example.com")
                .password("")
                .authorities(Usuario.ROLE_MODERATOR)
                .build();

        var response = controller.updateUser("user-1",
                new AdminUsersController.UpdateUserRequest(
                        "Persona", "persona@example.com", Usuario.ROLE_MODERATOR, true, "TRANSMETRO"),
                authenticatedModerator);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(target.getRole()).isEqualTo(Usuario.ROLE_USER);
        verify(usuarios, never()).save(target);
    }

    @Test
    void transportModeratorCannotChangeIdentity() {
        Usuario target = new Usuario("persona@example.com", "password-hash", "Persona");
        target.setId("user-1");
        Usuario moderator = new Usuario("moderador@example.com", "password-hash", "Moderador");
        moderator.setId("moderator-1");
        moderator.setRole(Usuario.ROLE_MODERATOR);
        moderator.setServicioAsignado("TRANSCARIBE");
        when(usuarios.findById("user-1")).thenReturn(Optional.of(target));
        when(usuarios.findByCorreoIgnoreCase("moderador@example.com")).thenReturn(Optional.of(moderator));
        var authenticatedModerator = User.withUsername("moderador@example.com")
                .password("")
                .authorities(Usuario.ROLE_MODERATOR)
                .build();

        var response = controller.updateUser("user-1",
                new AdminUsersController.UpdateUserRequest(
                        "Nombre cambiado", "persona@example.com", Usuario.ROLE_CONDUCTOR, true, null),
                authenticatedModerator);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(target.getRole()).isEqualTo(Usuario.ROLE_USER);
        verify(usuarios, never()).save(target);
    }

    @Test
    void transportModeratorCannotChangeModeratorFromAnotherService() {
        Usuario target = new Usuario("persona@example.com", "password-hash", "Persona");
        target.setId("user-1");
        target.setRole(Usuario.ROLE_MODERATOR);
        target.setServicioAsignado("TRANSMETRO");
        Usuario moderator = new Usuario("moderador@example.com", "password-hash", "Moderador");
        moderator.setId("moderator-1");
        moderator.setRole(Usuario.ROLE_MODERATOR);
        moderator.setServicioAsignado("TRANSCARIBE");
        when(usuarios.findById("user-1")).thenReturn(Optional.of(target));
        when(usuarios.findByCorreoIgnoreCase("moderador@example.com")).thenReturn(Optional.of(moderator));
        var authenticatedModerator = User.withUsername("moderador@example.com")
                .password("")
                .authorities(Usuario.ROLE_MODERATOR)
                .build();

        var response = controller.updateUser("user-1",
                new AdminUsersController.UpdateUserRequest(
                        "Persona", "persona@example.com", Usuario.ROLE_USER, true, null),
                authenticatedModerator);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(target.getRole()).isEqualTo(Usuario.ROLE_MODERATOR);
        verify(usuarios, never()).save(target);
    }
}
