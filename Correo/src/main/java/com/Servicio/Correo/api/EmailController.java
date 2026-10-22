package com.Servicio.Correo.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/emails")
public class EmailController {

    public static final String API_KEY_HEADER = "X-Mail-Service-Key";

    private final EmailSender emailSender;
    private final byte[] apiKey;

    public EmailController(EmailSender emailSender, @Value("${app.mail.api-key}") String apiKey) {
        if (apiKey.isBlank()) {
            throw new IllegalArgumentException("MAIL_SERVICE_API_KEY debe tener un valor.");
        }
        this.emailSender = emailSender;
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping
    public void send(@RequestHeader(name = API_KEY_HEADER, required = false) String suppliedApiKey,
            @Valid @RequestBody SendEmailRequest request) {
        if (suppliedApiKey == null || !MessageDigest.isEqual(
                apiKey, suppliedApiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credencial de servicio inválida.");
        }
        emailSender.send(request);
    }
}
