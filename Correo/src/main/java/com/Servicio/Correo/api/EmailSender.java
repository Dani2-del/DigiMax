package com.Servicio.Correo.api;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailSender {

    private static final int MAX_TOTAL_ATTACHMENT_BYTES = 5 * 1024 * 1024;

    private final JavaMailSender mailSender;
    private final String sender;

    public EmailSender(JavaMailSender mailSender,
            @Value("${spring.mail.from:${spring.mail.username:}}") String sender) {
        this.mailSender = mailSender;
        this.sender = sender;
    }

    public void send(SendEmailRequest request) {
        if (!StringUtils.hasText(request.text()) && !StringUtils.hasText(request.html())) {
            throw new IllegalArgumentException("El correo debe incluir contenido de texto o HTML.");
        }

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(request.to().toArray(String[]::new));
            helper.setSubject(request.subject());
            if (StringUtils.hasText(sender)) {
                helper.setFrom(sender);
            }
            if (StringUtils.hasText(request.text()) && StringUtils.hasText(request.html())) {
                helper.setText(request.text(), request.html());
            } else if (StringUtils.hasText(request.html())) {
                helper.setText(request.html(), true);
            } else {
                helper.setText(request.text());
            }

            int attachmentBytes = 0;
            int encodedAttachmentBytes = 0;
            if (request.attachments() != null) {
                for (EmailAttachment attachment : request.attachments()) {
                    encodedAttachmentBytes += attachment.contentBase64().length();
                    if (encodedAttachmentBytes > ((MAX_TOTAL_ATTACHMENT_BYTES + 2) / 3) * 4) {
                        throw new IllegalArgumentException("Los archivos adjuntos superan el límite de 5 MB.");
                    }
                    byte[] content = Base64.getDecoder().decode(attachment.contentBase64());
                    attachmentBytes += content.length;
                    if (attachmentBytes > MAX_TOTAL_ATTACHMENT_BYTES) {
                        throw new IllegalArgumentException("Los archivos adjuntos superan el límite de 5 MB.");
                    }
                    ByteArrayResource resource = new ByteArrayResource(content);
                    if (StringUtils.hasText(attachment.contentId())) {
                        helper.addInline(attachment.contentId(), resource, attachment.contentType());
                    } else {
                        helper.addAttachment(attachment.fileName(), resource, attachment.contentType());
                    }
                }
            }
            mailSender.send(message);
        } catch (MessagingException exception) {
            throw new IllegalStateException("No se pudo preparar el correo.", exception);
        } catch (MailException exception) {
            throw new IllegalStateException("No se pudo enviar el correo.", exception);
        }
    }
}
