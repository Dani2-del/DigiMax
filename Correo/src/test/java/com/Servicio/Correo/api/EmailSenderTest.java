package com.Servicio.Correo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.BodyPart;
import jakarta.mail.internet.MimeMessage;

class EmailSenderTest {

    private final JavaMailSender mailSender = org.mockito.Mockito.mock(JavaMailSender.class);
    private final MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(message);
        emailSender = new EmailSender(mailSender, "notificaciones@example.com");
    }

    @Test
    void sendsHtmlEmailWithConfiguredSender() throws Exception {
        emailSender.send(new SendEmailRequest(
                List.of("person@example.com"), "Test subject", null, "<p>Contenido</p>", List.of()));

        ArgumentCaptor<MimeMessage> sentMessage = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sentMessage.capture());
        assertThat(sentMessage.getValue().getSubject()).isEqualTo("Test subject");
        assertThat(sentMessage.getValue().getFrom()[0].toString()).isEqualTo("notificaciones@example.com");
        assertThat(sentMessage.getValue().getAllRecipients()[0].toString()).isEqualTo("person@example.com");
    }

    @Test
    void includesInlineImagesInHtmlEmails() throws Exception {
        EmailAttachment image = new EmailAttachment(
                "notice.png", "image/png", Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}), "noticeImage");
        emailSender.send(new SendEmailRequest(
                List.of("person@example.com"), "Notice", null, "<img src='cid:noticeImage'>", List.of(image)));

        ArgumentCaptor<MimeMessage> sentMessage = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sentMessage.capture());
        assertThat(sentMessage.getValue().getContent()).isInstanceOf(Multipart.class);
        assertThat(countParts((Multipart) sentMessage.getValue().getContent())).isGreaterThan(1);
    }

    private int countParts(Multipart multipart) throws Exception {
        int count = multipart.getCount();
        for (int index = 0; index < multipart.getCount(); index++) {
            BodyPart part = multipart.getBodyPart(index);
            if (part.getContent() instanceof Multipart nested) {
                count += countParts(nested);
            }
        }
        return count;
    }
}
