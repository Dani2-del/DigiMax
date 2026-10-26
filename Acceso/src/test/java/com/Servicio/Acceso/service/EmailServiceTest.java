package com.Servicio.Acceso.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailServiceTest {

    @Test
    void createsBrandedHtmlAndPreservesPlainTextFallback() {
        EmailService.EmailContent content = EmailService.crearContenido(
                "Verifica tu cuenta Digitrans",
                "Hola Danier,\n\nTu código de verificación es: 681598\n\nEste código vence en 10 minutos.");

        assertThat(content.text()).contains("Tu código de verificación es: 681598");
        assertThat(content.html())
                .contains("DIGITRANS")
                .contains("Verifica tu cuenta Digitrans")
                .contains("background:#172235")
                .contains("border-bottom:4px solid #f59a23")
                .contains(">681598</span>")
                .contains("Este código vence en 10 minutos.");
    }

    @Test
    void escapesUserProvidedTextBeforeRenderingHtml() {
        EmailService.EmailContent content = EmailService.crearContenido(
                "Aviso Digitrans", "Hola <script>alert('x')</script> & bienvenida");

        assertThat(content.html())
                .contains("&lt;script&gt;")
                .contains("&amp; bienvenida")
                .doesNotContain("<script>");
    }
}
