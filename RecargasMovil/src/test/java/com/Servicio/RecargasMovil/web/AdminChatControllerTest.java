package com.Servicio.RecargasMovil.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import com.Servicio.RecargasMovil.model.AdminChatMessage;
import com.Servicio.RecargasMovil.security.AuthenticatedIdentity;

@ExtendWith(MockitoExtension.class)
class AdminChatControllerTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private AdminChatController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminChatController(mongoTemplate, "http://acceso.test");
    }

    @Test
    void chatPageIsAvailableToModerators() {
        AuthenticatedIdentity identity =
                new AuthenticatedIdentity("moderator-1", "mod@example.test", "Moderador", "ROLE_MODERATOR");
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.chat(identity, model)).isEqualTo("admin-chat");
        assertThat(model.getAttribute("identity")).isSameAs(identity);
    }

    @Test
    void sendsTrimmedMessageWithAuthorDetails() {
        AuthenticatedIdentity identity =
                new AuthenticatedIdentity("admin-1", "admin@example.test", "Administradora", "ROLE_ADMIN");

        AdminChatController.MessageResponse response = controller.sendMessage("  Hola equipo  ", identity);

        ArgumentCaptor<AdminChatMessage> messageCaptor = ArgumentCaptor.forClass(AdminChatMessage.class);
        verify(mongoTemplate).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getAuthorId()).isEqualTo("admin-1");
        assertThat(messageCaptor.getValue().getAuthorRole()).isEqualTo("Administrador");
        assertThat(messageCaptor.getValue().getText()).isEqualTo("Hola equipo");
        assertThat(response.text()).isEqualTo("Hola equipo");
    }

    @Test
    void listsMessagesOldestFirstForChatDisplay() {
        AdminChatMessage older = message("older", LocalDateTime.of(2026, 10, 25, 10, 0));
        AdminChatMessage newer = message("newer", LocalDateTime.of(2026, 10, 25, 11, 0));
        when(mongoTemplate.find(any(Query.class), eq(AdminChatMessage.class)))
                .thenReturn(List.of(newer, older));

        List<AdminChatController.MessageResponse> messages = controller.messages(
                new AuthenticatedIdentity("admin-1", "admin@example.test", "Admin", "ROLE_ADMIN"));

        assertThat(messages).extracting(AdminChatController.MessageResponse::text)
                .containsExactly("older", "newer");
    }

    @Test
    void rejectsUsersOutsideAdminAndModeratorRoles() {
        assertThatThrownBy(() -> controller.chat(
                new AuthenticatedIdentity("user-1", "user@example.test", "Usuario", "ROLE_USER"),
                new ConcurrentModel()))
                .isInstanceOf(ResponseStatusException.class);
    }

    private static AdminChatMessage message(String text, LocalDateTime createdAt) {
        AdminChatMessage message = new AdminChatMessage();
        message.setAuthorId("admin-1");
        message.setAuthorName("Admin");
        message.setAuthorRole("Administrador");
        message.setText(text);
        message.setCreatedAt(createdAt);
        return message;
    }
}
