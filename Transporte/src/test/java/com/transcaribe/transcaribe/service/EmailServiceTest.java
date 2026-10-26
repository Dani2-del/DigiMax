package com.transcaribe.transcaribe.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailServiceTest {

    @Test
    void wrapsTransportNotificationsInDigitransEmailDesign() {
        EmailService.EmailContent content = EmailService.crearContenido(
                "Nueva ruta asignada",
                "<div><p>Hola <strong>Danier</strong>,</p><p>Ruta: Centro &amp; Norte</p></div>");

        assertThat(content.html())
                .contains("DIGITRANS")
                .contains("TU CIUDAD, A TU ALCANCE")
                .contains("Nueva ruta asignada")
                .contains("background:#172235")
                .contains("#f59a23")
                .contains("Centro &amp; Norte");
        assertThat(content.text()).contains("Hola Danier,").contains("Ruta: Centro & Norte");
    }

    @Test
    void escapesEmailSubjectInHtmlHeader() {
        EmailService.EmailContent content = EmailService.crearContenido(
                "Aviso <importante>", "<p>Mensaje</p>");

        assertThat(content.html())
                .contains("Aviso &lt;importante&gt;")
                .doesNotContain("<h1 style=\"margin:12px 0 0;font-size:21px;line-height:1.35;color:#ffffff;\">"
                        + "Aviso <importante>");
    }
}
