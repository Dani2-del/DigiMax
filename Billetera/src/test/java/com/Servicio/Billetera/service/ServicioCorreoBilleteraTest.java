package com.Servicio.Billetera.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ServicioCorreoBilleteraTest {

    @Test
    void createsStyledHtmlAndPlainTextFallback() {
        ServicioCorreoBilletera.EmailContent content =
                ServicioCorreoBilletera.crearContenido("Hola Danier", "congelar tu tarjeta", "123456");

        assertThat(content.text())
                .contains("Tu código para congelar tu tarjeta es: 123456")
                .contains("vence en 10 minutos");
        assertThat(content.html())
                .contains("Verificación de seguridad")
                .contains("congelar tu tarjeta")
                .contains("123456")
                .contains("background:#172235")
                .contains("#f59a23")
                .contains("No compartas este código con nadie.");
    }

    @Test
    void escapesDynamicValuesInHtml() {
        ServicioCorreoBilletera.EmailContent content =
                ServicioCorreoBilletera.crearContenido("Hola <Danier>", "congelar & activar", "123456");

        assertThat(content.html())
                .contains("Hola &lt;Danier&gt;")
                .contains("congelar &amp; activar")
                .doesNotContain("Hola <Danier>");
    }
}
