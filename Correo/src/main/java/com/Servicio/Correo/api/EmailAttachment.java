package com.Servicio.Correo.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailAttachment(
        @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "^[^/\\\\\\r\\n]+$")
        String fileName,
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+$")
        String contentType,
        @NotBlank
        String contentBase64,
        @Pattern(regexp = "^[A-Za-z0-9._-]+$")
        String contentId) {
}
