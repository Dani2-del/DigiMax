package com.Servicio.Correo.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendEmailRequest(
        @NotEmpty
        @Size(max = 50)
        List<@NotBlank @Email String> to,
        @NotBlank
        @Size(max = 250)
        @Pattern(regexp = "^[^\\r\\n]*$")
        String subject,
        @Size(max = 100_000)
        String text,
        @Size(max = 100_000)
        String html,
        @Size(max = 3)
        List<@Valid EmailAttachment> attachments) {
}
