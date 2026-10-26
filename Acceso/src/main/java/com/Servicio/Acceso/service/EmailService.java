package com.Servicio.Acceso.service;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonInclude;

@Service
public class EmailService {

    private static final Pattern VERIFICATION_CODE = Pattern.compile("\\b\\d{6}\\b");

    private final RestClient restClient;
    private final String apiKey;

    public EmailService(
            @Value("${app.mail.base-url}") String mailServiceUrl,
            @Value("${app.mail.api-key}") String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("MAIL_SERVICE_API_KEY debe tener un valor.");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(mailServiceUrl)
                .requestFactory(requestFactory)
                .build();
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
        EmailContent content = crearContenido(asunto, texto);
        try {
            restClient.post()
                    .uri("/api/emails")
                    .header("X-Mail-Service-Key", apiKey)
                    .body(new SendEmailRequest(
                            List.of(destinatario), asunto, content.text(), content.html()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new MailDeliveryException("No se pudo enviar el correo solicitado.", exception);
        }
    }

    static EmailContent crearContenido(String asunto, String texto) {
        Matcher matcher = VERIFICATION_CODE.matcher(texto);
        StringBuilder contenidoHtml = new StringBuilder();
        int inicio = 0;
        while (matcher.find()) {
            contenidoHtml.append(escaparHtml(texto.substring(inicio, matcher.start())));
            contenidoHtml.append("<span style=\"display:inline-block;padding:8px 12px;border:1px solid #f4c27e;"
                    + "border-radius:8px;background:#fff8ed;color:#c66a0a;font-size:20px;font-weight:bold;"
                    + "letter-spacing:5px;\">")
                    .append(matcher.group())
                    .append("</span>");
            inicio = matcher.end();
        }
        contenidoHtml.append(escaparHtml(texto.substring(inicio)));
        String html = """
                <!doctype html>
                <html lang="es">
                <body style="margin:0;padding:32px 12px;background:#eef1f5;font-family:Arial,Helvetica,sans-serif;color:#172235;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-collapse:collapse;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:580px;border:1px solid #e3eaf0;border-radius:20px;background:#ffffff;overflow:hidden;">
                        <tr><td style="padding:25px 32px;background:#172235;border-bottom:4px solid #f59a23;color:#ffffff;">
                          <p style="margin:0;color:#f59a23;font-size:12px;font-weight:bold;letter-spacing:2px;">DIGITRANS</p>
                          <h1 style="margin:10px 0 0;font-size:22px;line-height:1.35;color:#ffffff;">%s</h1>
                        </td></tr>
                        <tr><td style="padding:30px 32px;font-size:15px;line-height:1.8;color:#455267;">
                          <div style="white-space:pre-line;overflow-wrap:anywhere;">%s</div>
                        </td></tr>
                        <tr><td style="padding:16px 32px;border-top:1px solid #edf1f4;color:#8490a0;font-size:12px;line-height:1.5;">
                          Mensaje automático · Digitrans · Cartagena de Indias, Colombia
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escaparHtml(asunto), contenidoHtml);
        return new EmailContent(texto, html);
    }

    private static String escaparHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    record EmailContent(String text, String html) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SendEmailRequest(List<String> to, String subject, String text, String html) {
    }
}
