package com.Servicio.Billetera.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;

import com.Servicio.Billetera.model.AdminChatMessage;
import com.Servicio.Billetera.security.AuthenticatedIdentity;

class AdminChatControllerTest {

    private final MongoTemplate mongoTemplate = org.mockito.Mockito.mock(MongoTemplate.class);
    private final AdminChatController controller =
            new AdminChatController(mongoTemplate, "https://acceso.example");

    @Test
    void adminsAndModeratorsCanSendMessages() {
        AuthenticatedIdentity moderator =
                new AuthenticatedIdentity("mod-1", "mod@example.com", "Moderador", "ROLE_MODERATOR");

        var response = controller.sendMessage("  Revisar el caso  ", moderator);

        assertThat(response.authorId()).isEqualTo("mod-1");
        assertThat(response.authorName()).isEqualTo("Moderador");
        assertThat(response.authorRole()).isEqualTo("Moderador");
        assertThat(response.text()).isEqualTo("Revisar el caso");
        assertThat(response.createdAt()).isNotBlank();
        var savedMessage = org.mockito.ArgumentCaptor.forClass(AdminChatMessage.class);
        verify(mongoTemplate).save(savedMessage.capture());
        assertThat(savedMessage.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void rejectsInvalidMessagesAndUnauthenticatedRoles() {
        AuthenticatedIdentity user =
                new AuthenticatedIdentity("user-1", "user@example.com", "Usuario", "ROLE_USER");

        assertThatThrownBy(() -> controller.sendMessage("mensaje", user))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
        assertThatThrownBy(() -> controller.sendMessage("  ", new AuthenticatedIdentity(
                "admin", "admin@example.com", "Admin", "ROLE_ADMIN")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> controller.sendMessage("x".repeat(1001), new AuthenticatedIdentity(
                "admin", "admin@example.com", "Admin", "ROLE_ADMIN")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void messagesAreReturnedInChronologicalOrder() {
        AdminChatMessage older = message("1", "Primero", LocalDateTime.of(2026, 10, 25, 10, 0));
        AdminChatMessage newer = message("2", "Segundo", LocalDateTime.of(2026, 10, 25, 10, 1));
        when(mongoTemplate.find(any(Query.class), eq(AdminChatMessage.class)))
                .thenReturn(List.of(newer, older));

        var responses = controller.messages(
                new AuthenticatedIdentity("admin", "admin@example.com", "Admin", "ROLE_ADMIN"));

        assertThat(responses).extracting(AdminChatController.MessageResponse::text)
                .containsExactly("Primero", "Segundo");
    }

    private static AdminChatMessage message(String id, String text, LocalDateTime createdAt) {
        AdminChatMessage message = new AdminChatMessage();
        message.setId(id);
        message.setAuthorId("admin");
        message.setAuthorName("Admin");
        message.setAuthorRole("Administrador");
        message.setText(text);
        message.setCreatedAt(createdAt);
        return message;
    }
}
