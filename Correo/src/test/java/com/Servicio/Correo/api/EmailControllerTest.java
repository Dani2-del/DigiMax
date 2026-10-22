package com.Servicio.Correo.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class EmailControllerTest {

    private static final String API_KEY = "test-mail-api-key";

    private final EmailSender emailSender = mock(EmailSender.class);
    private final EmailController controller = new EmailController(emailSender, API_KEY);

    @Test
    void sendsEmailOnlyWithConfiguredServiceKey() {
        SendEmailRequest request = new SendEmailRequest(
                List.of("person@example.com"), "Test", "Body", null, null);

        controller.send(API_KEY, request);

        verify(emailSender).send(request);
    }

    @Test
    void rejectsRequestsWithoutValidServiceKey() {
        SendEmailRequest request = new SendEmailRequest(
                List.of("person@example.com"), "Test", "Body", null, null);

        assertThatThrownBy(() -> controller.send("wrong-key", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
        verifyNoInteractions(emailSender);
    }
}
