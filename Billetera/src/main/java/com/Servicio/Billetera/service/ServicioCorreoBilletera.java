package com.Servicio.Billetera.service;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.Servicio.Billetera.model.AccionVerificacionTarjeta;

@Component
public class ServicioCorreoBilletera {

    private final RestClient restClient;
    private final String apiKey;

    public ServicioCorreoBilletera(
            @Value("${app.mail.base-url}") String mailServiceUrl,
            @Value("${app.mail.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder()
                .baseUrl(mailServiceUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
    }

    public void enviarCodigoTarjeta(
            String correo, String nombre, String codigo, AccionVerificacionTarjeta accion) {
        if (!StringUtils.hasText(apiKey)) {
            throw new MailDeliveryException("Configura MAIL_SERVICE_API_KEY para enviar códigos de tarjeta.");
        }
        String saludo = StringUtils.hasText(nombre) ? "Hola " + nombre.trim() : "Hola";
        String subject = switch (accion) {
            case CREAR -> "Verifica tu tarjeta Digitrans";
            case DESACTIVAR -> "Confirma la desactivación de tu tarjeta Digitrans";
            case CONGELAR -> "Confirma la congelación de tu tarjeta Digitrans";
            case DESCONGELAR -> "Confirma la activación de tu tarjeta Digitrans";
        };
        String purpose = switch (accion) {
            case CREAR -> "activar y usar tu nueva tarjeta";
            case DESACTIVAR -> "desactivar tu tarjeta";
            case CONGELAR -> "congelar tu tarjeta";
            case DESCONGELAR -> "descongelar tu tarjeta";
        };
        EmailContent content = crearContenido(saludo, purpose, codigo);
        try {
            restClient.post()
                    .uri("/api/emails")
                    .header("X-Mail-Service-Key", apiKey)
                    .body(new SendEmailRequest(List.of(correo), subject, content.text(), content.html()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new MailDeliveryException("No se pudo enviar el código de verificación por correo.", exception);
        }
    }

    static EmailContent crearContenido(String saludo, String purpose, String codigo) {
        String text = saludo + ",\n\nTu código para " + purpose + " es: " + codigo
                + "\n\nEl código vence en 10 minutos y solo se puede usar una vez. "
                + "Si no solicitaste esta acción, ignora este mensaje.";
        String html = """
                <!doctype html>
                <html lang="es">
                <body style="margin:0;padding:32px 12px;background:#eef1f5;font-family:Arial,Helvetica,sans-serif;color:#172235;">
                  <div style="max-width:560px;margin:0 auto;background:#ffffff;border:1px solid #e3eaf0;border-radius:20px;overflow:hidden;">
                    <div style="padding:25px 32px;background:#172235;color:#ffffff;">
                      <p style="margin:0;color:#f59a23;font-size:12px;font-weight:bold;letter-spacing:2px;">DIGITRANS</p>
                      <h1 style="margin:10px 0 0;font-size:23px;line-height:1.3;">Verificación de seguridad</h1>
                    </div>
                    <div style="padding:30px 32px;">
                      <p style="margin:0 0 18px;font-size:16px;line-height:1.6;">%s,</p>
                      <p style="margin:0 0 22px;font-size:15px;line-height:1.6;color:#536174;">
                        Usa este código para <strong style="color:#172235;">%s</strong>:
                      </p>
                      <div style="padding:19px 12px;border:1px solid #f4c27e;border-radius:14px;background:#fff8ed;text-align:center;">
                        <span style="color:#c66a0a;font-family:Arial,Helvetica,sans-serif;font-size:34px;font-weight:bold;letter-spacing:9px;">%s</span>
                      </div>
                      <p style="margin:20px 0 0;font-size:14px;line-height:1.6;color:#536174;">
                        El código vence en <strong>10 minutos</strong> y solo se puede usar una vez.
                      </p>
                      <div style="margin-top:22px;padding:14px 16px;border-left:3px solid #f59a23;border-radius:8px;background:#f4f6f8;color:#455267;font-size:13px;line-height:1.5;">
                        Si no solicitaste esta acción, ignora este mensaje. No compartas este código con nadie.
                      </div>
                    </div>
                    <div style="padding:16px 32px;border-top:1px solid #edf1f4;color:#8490a0;font-size:12px;line-height:1.5;">
                      Mensaje automático de seguridad · Digitrans Billetera
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(escaparHtml(saludo), escaparHtml(purpose), escaparHtml(codigo));
        return new EmailContent(text, html);
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
