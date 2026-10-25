package com.Servicio.Acceso.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonInclude;

@Service
public class EmailService {

    private final RestClient restClient;
    private final String apiKey;

    public EmailService(
            @Value("${app.mail.base-url}") String mailServiceUrl,
            @Value("${app.mail.api-key}") String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("MAIL_SERVICE_API_KEY debe tener un valor.");
        }
        this.restClient = RestClient.builder().baseUrl(mailServiceUrl).build();
        this.apiKey = apiKey;
    }

    public void enviarCodigoVerificacion(String correo, String nombre, String codigo) {
        enviar(correo, "Verifica tu cuenta Digitrans",
                "Hola " + nombre + ",\n\nTu código de verificación es: " + codigo
                        + "\n\nEste código vence en 10 minutos.");
    }

    public void enviarCodigoRecuperacion(String correo, String nombre, String codigo) {
        enviar(correo, "Recupera tu cuenta Digitrans",
                "Hola " + nombre + ",\n\nTu código para restablecer la contraseña es: " + codigo
                        + "\n\nEste código vence en 15 minutos. Si no solicitaste este cambio, ignora este mensaje.");
    }

    public void enviarCodigoCambioPassword(String correo, String nombre, String codigo) {
        enviar(correo, "Cambio de contraseña Digitrans",
                "Hola " + nombre + ",\n\nTu código para confirmar el cambio de contraseña es: " + codigo
                        + "\n\nEste código vence en 10 minutos.");
    }

    public void enviarCodigoCambioPerfil(String correo, String nombre, String codigo) {
        enviar(correo, "Confirma los cambios de tu cuenta Digitrans",
                "Hola " + nombre + ",\n\nTu código para confirmar los cambios de tu cuenta es: " + codigo
                        + "\n\nEste código vence en 10 minutos.");
    }

    public void enviarAvisoCambioSitmModerador(
            String correo, String nombre, String sitmActual, String sitmSolicitado) {
        enviar(correo, "Solicitud de cambio de SITM no aplicada",
                "Hola " + nombre + ",\n\nRecibimos una solicitud para cambiar tu SITM de "
                        + sitmActual + " a " + sitmSolicitado + ".\n\nNo aplicamos el cambio porque tu cuenta "
                        + "es moderadora de " + sitmActual + " y tu SITM está vinculado a esa responsabilidad. "
                        + "Si necesitas cambiarlo, contacta al equipo de soporte para que revise y aplique "
                        + "la solicitud.\n\nTu asignación actual no ha cambiado.");
    }

    @org.springframework.scheduling.annotation.Async
    public void enviarNotificacionLogin(String correo, String nombre) {
        enviar(correo, "Nuevo inicio de sesión en Digitrans",
                "Hola " + nombre + ",\n\nSe inició una sesión en tu cuenta Digitrans. "
                        + "Si no fuiste tú, cambia tu contraseña cuanto antes.");
    }

    private void enviar(String destinatario, String asunto, String texto) {
        try {
            restClient.post()
                    .uri("/api/emails")
                    .header("X-Mail-Service-Key", apiKey)
                    .body(new SendEmailRequest(List.of(destinatario), asunto, texto, null))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new MailDeliveryException("No se pudo enviar el correo solicitado.", exception);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SendEmailRequest(List<String> to, String subject, String text, String html) {
    }
}
