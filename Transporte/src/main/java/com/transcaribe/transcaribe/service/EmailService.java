package com.transcaribe.transcaribe.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.transcaribe.transcaribe.Model.SistemaTransporte;

@Service
public class EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    private final RestClient restClient;
    private final String apiKey;

    public EmailService(
            @Value("${app.mail.base-url:http://localhost:8084}") String mailServiceUrl,
            @Value("${app.mail.api-key}") String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalArgumentException("MAIL_SERVICE_API_KEY debe tener un valor.");
        }
        this.restClient = RestClient.builder().baseUrl(mailServiceUrl).build();
        this.apiKey = apiKey;
    }

    @org.springframework.scheduling.annotation.Async
    public void enviarAvisosAdministrativosEnSegundoPlano(String avisoId,
            List<DestinatarioAviso> destinatarios, String asunto, String mensajeAviso,
            byte[] imagen, String tipoContenidoImagen) {
        int enviados = 0;
        int fallidos = 0;
        for (DestinatarioAviso destinatario : destinatarios) {
            try {
                enviarAvisoAdministrativo(destinatario.correo(), destinatario.nombre(),
                        asunto, mensajeAviso, imagen, tipoContenidoImagen);
                enviados++;
            } catch (IllegalStateException exception) {
                fallidos++;
                LOGGER.warn("No se pudo enviar el aviso administrativo {} al destinatario {}",
                        avisoId, destinatario.usuarioId(), exception);
            }
        }
        LOGGER.info("Envío de correos del aviso administrativo {} completado: {} enviados, {} fallidos.",
                avisoId, enviados, fallidos);
    }

    public void enviarAvisoAdministrativo(String destinatario, String nombre,
            String asunto, String mensajeAviso) {
        enviarAvisoAdministrativo(destinatario, nombre, asunto, mensajeAviso, null, null);
    }

    public void enviarAvisoAdministrativo(String destinatario, String nombre,
            String asunto, String mensajeAviso, byte[] imagen, String tipoContenidoImagen) {
        String mensajeHtml = HtmlUtils.htmlEscape(mensajeAviso).replace("\n", "<br>");
        String contenidoHtml =
                "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 24px; max-width: 620px;'>"
                + "<h2 style='color: #b75c16;'>Transcaribe · Aviso importante</h2>"
                + "<p>Hola <strong>" + HtmlUtils.htmlEscape(nombre) + "</strong>,</p>"
                + (imagen == null ? "" : "<img src='cid:imagenAviso' alt='Imagen del aviso' style='display:block; max-width:100%; height:auto; margin: 0 0 20px; border-radius:8px;'>")
                + "<div style='white-space: pre-wrap; line-height: 1.6;'>" + mensajeHtml + "</div>"
                + "<p style='font-size: 12px; color: #777; margin-top: 24px;'>Mensaje enviado por la administración de Transcaribe.</p>"
                + "</div>";
        List<EmailAttachment> attachments = imagen == null ? List.of()
                : List.of(new EmailAttachment("aviso", tipoContenidoImagen,
                        Base64.getEncoder().encodeToString(imagen), "imagenAviso"));
        send(destinatario, asunto, contenidoHtml, attachments);
    }

    public record DestinatarioAviso(String usuarioId, String correo, String nombre) {
    }

    @org.springframework.scheduling.annotation.Async
    public void enviarNotificacionHorarioAsignado(String destinatario, String nombre, String ruta,
            LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, SistemaTransporte sistema) {
        enviarNotificacionHorario(destinatario, nombre, ruta, fecha, horaInicio, horaFin,
                "📅 Nueva ruta asignada - " + sistema.getNombre(),
                "Se te ha asignado una nueva ruta.", sistema);
    }

    @org.springframework.scheduling.annotation.Async
    public void enviarNotificacionHorarioActualizado(String destinatario, String nombre, String ruta,
            LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, SistemaTransporte sistema) {
        enviarNotificacionHorario(destinatario, nombre, ruta, fecha, horaInicio, horaFin,
                "✏️ Ruta actualizada - " + sistema.getNombre(),
                "El administrador ha actualizado uno de tus horarios.", sistema);
    }

    public void enviarReporteConductor(List<String> destinatarios, String nombreConductor,
            String correoConductor, String asunto, String detalle, SistemaTransporte sistema) {
        if (destinatarios == null || destinatarios.isEmpty()) {
            throw new IllegalStateException(
                    "No hay administradores o moderadores activos de este servicio para recibir el reporte.");
        }
        if (asunto == null || asunto.isBlank() || detalle == null || detalle.isBlank()) {
            throw new IllegalArgumentException("El asunto y el detalle del reporte son obligatorios.");
        }

        String contenidoHtml =
                "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 620px;'>"
                + "<h2 style='color: #0f3948;'>Reporte de conductor - "
                + HtmlUtils.htmlEscape(sistema.getNombre()) + "</h2>"
                + "<p><strong>Conductor:</strong> " + HtmlUtils.htmlEscape(nombreConductor) + "</p>"
                + "<p><strong>Correo:</strong> " + HtmlUtils.htmlEscape(correoConductor) + "</p>"
                + "<p><strong>Asunto:</strong> " + HtmlUtils.htmlEscape(asunto.trim()) + "</p>"
                + "<div style='background-color: #f8f9fa; padding: 15px; border-radius: 6px; white-space: pre-wrap;'>"
                + HtmlUtils.htmlEscape(detalle.trim()) + "</div>"
                + "<p style='font-size: 12px; color: #888; margin-top: 20px;'>Reporte enviado desde el panel del conductor.</p>"
                + "</div>";
        send(destinatarios, "[" + sistema.getNombre() + " · Reporte de conductor] " + asunto.trim(),
                contenidoHtml, List.of());
    }

    private void enviarNotificacionHorario(String destinatario, String nombre, String ruta,
            LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
            String asunto, String mensaje, SistemaTransporte sistema) {
        if (!StringUtils.hasText(destinatario)) {
            LOGGER.warn("No se envió la notificación de horario: el conductor no tiene correo.");
            return;
        }
        String contenidoHtml =
                "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 560px;'>"
                + "<h2 style='color: #2a9d8f; text-align: center;'>" + HtmlUtils.htmlEscape(sistema.getNombre()) + "</h2>"
                + "<p>Hola <strong>" + HtmlUtils.htmlEscape(nombre) + "</strong>,</p>"
                + "<p>" + HtmlUtils.htmlEscape(mensaje) + "</p>"
                + "<div style='background-color: #f1f8f9; padding: 15px; border-radius: 6px; border-left: 5px solid #2a9d8f;'>"
                + "<p style='margin: 0 0 8px 0;'><strong>Detalles del horario:</strong></p>"
                + "<p style='margin: 4px 0;'>🚌 <strong>Ruta:</strong> " + HtmlUtils.htmlEscape(ruta) + "</p>"
                + "<p style='margin: 4px 0;'>📆 <strong>Fecha:</strong> " + fecha + "</p>"
                + "<p style='margin: 4px 0;'>🕐 <strong>Horario:</strong> " + horaInicio + " - " + horaFin + "</p>"
                + "</div>"
                + "<p style='font-size: 12px; color: #888; margin-top: 20px;'>Este es un correo automático de "
                + HtmlUtils.htmlEscape(sistema.getNombre()) + ".</p></div>";
        sendOrLog(destinatario, asunto, contenidoHtml);
    }

    @org.springframework.scheduling.annotation.Async
    public void enviarNotificacionRecarga(String destinatario, String nombre, double monto, String saldoActual) {
        String contenidoHtml =
                "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 500px;'>"
                + "<h2 style='color: #2b9348; text-align: center;'>¡Recarga Exitosa!</h2>"
                + "<p>Hola <strong>" + HtmlUtils.htmlEscape(nombre) + "</strong>, has recargado tu cuenta correctamente.</p>"
                + "<div style='background-color: #f1f8e9; padding: 15px; border-radius: 5px; border-left: 5px solid #2b9348;'>"
                + "💰 <strong>Monto recargado:</strong> $" + monto + "<br>"
                + "💳 <strong>Nuevo saldo:</strong> $" + HtmlUtils.htmlEscape(saldoActual) + "</div></div>";
        sendOrLog(destinatario, "✅ Recarga exitosa - Transcaribe", contenidoHtml);
    }

    @org.springframework.scheduling.annotation.Async
    public void enviarNotificacionGasto(String destinatario, String nombre, double monto, String saldoRestante) {
        String contenidoHtml =
                "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 500px;'>"
                + "<h3 style='color: #e63946;'>Uso de saldo</h3>"
                + "<p>Hola <strong>" + HtmlUtils.htmlEscape(nombre) + "</strong>, se ha descontado un valor de tu saldo por uso del servicio.</p>"
                + "<table style='width: 100%; border-collapse: collapse;'>"
                + "<tr><td style='padding: 5px;'>Monto descontado:</td><td style='text-align: right;'><strong>-$" + monto + "</strong></td></tr>"
                + "<tr><td style='padding: 5px;'>Saldo disponible:</td><td style='text-align: right;'>$" + HtmlUtils.htmlEscape(saldoRestante) + "</td></tr>"
                + "</table></div>";
        sendOrLog(destinatario, "Ticket de Viaje - Transcaribe", contenidoHtml);
    }

    @org.springframework.scheduling.annotation.Async
    public void enviarNotificacionRutaIniciada(String destinatario, String nombre, String ruta, String sentido,
            String placaBus, String horaProgramada) {
        String contenidoHtml =
                "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 500px;'>"
                + "<h2 style='color: #ff8c00; text-align: center;'>🚌 ¡Tu ruta favorita va en camino!</h2>"
                + "<p>Hola <strong>" + HtmlUtils.htmlEscape(nombre) + "</strong>,</p>"
                + "<p>El bus de la ruta <strong>" + HtmlUtils.htmlEscape(ruta) + "</strong> en sentido <strong>"
                + HtmlUtils.htmlEscape(sentido) + "</strong> (placa " + HtmlUtils.htmlEscape(placaBus)
                + ") acaba de iniciar su recorrido.</p>"
                + "<p>Hora de salida programada: <strong>" + HtmlUtils.htmlEscape(horaProgramada) + "</strong>.</p>"
                + "<p style='font-size: 13px; color: #555;'>Te avisamos porque tienes esta ruta guardada en tus favoritas.</p>"
                + "</div>";
        sendOrLog(destinatario, "🚌 Tu ruta favorita " + ruta + " acaba de salir - Transcaribe", contenidoHtml);
    }

    @org.springframework.scheduling.annotation.Async
    public void enviarAlertaSaldoBajo(String destinatario, String nombre, String numeroTarjeta,
            String saldoActual, String limite) {
        String tarjetaOculta = numeroTarjeta != null && numeroTarjeta.length() >= 4
                ? "**** **** " + HtmlUtils.htmlEscape(numeroTarjeta.substring(numeroTarjeta.length() - 4))
                : "****";
        String contenidoHtml =
                "<div style='font-family: Arial, sans-serif; border: 1px solid #ddd; border-radius: 10px; padding: 20px; max-width: 500px;'>"
                + "<h2 style='color: #d97706; text-align: center;'>⚠️ Alerta de Saldo Bajo</h2>"
                + "<p>Hola <strong>" + HtmlUtils.htmlEscape(nombre) + "</strong>,</p>"
                + "<p>Tu tarjeta de Transcaribe ha alcanzado o superado el límite de saldo mínimo configurado.</p>"
                + "<div style='background-color: #fffbeeb0; padding: 15px; border-radius: 5px; border-left: 5px solid #d97706; margin: 15px 0;'>"
                + "💳 <strong>Tarjeta:</strong> " + tarjetaOculta + "<br>"
                + "💰 <strong>Saldo actual:</strong> $" + HtmlUtils.htmlEscape(saldoActual) + " COP<br>"
                + "⚙️ <strong>Límite configurado:</strong> $" + HtmlUtils.htmlEscape(limite) + " COP</div>"
                + "<p style='font-size: 13px; color: #555;'>Te recomendamos realizar una recarga pronto para continuar viajando sin inconvenientes.</p>"
                + "</div>";
        sendOrLog(destinatario, "⚠️ Alerta de Saldo Bajo - Transcaribe", contenidoHtml);
    }

    private void sendOrLog(String destinatario, String subject, String html) {
        if (!StringUtils.hasText(destinatario)) {
            LOGGER.warn("No se envió el correo '{}': el destinatario está vacío.", subject);
            return;
        }
        try {
            send(destinatario, subject, html, List.of());
        } catch (IllegalStateException exception) {
            LOGGER.error("No se pudo enviar el correo '{}'.", subject, exception);
        }
    }

    private void send(String destinatario, String subject, String html, List<EmailAttachment> attachments) {
        send(List.of(destinatario), subject, html, attachments);
    }

    private void send(List<String> recipients, String subject, String html, List<EmailAttachment> attachments) {
        EmailContent content = crearContenido(subject, html);
        try {
            restClient.post()
                    .uri("/api/emails")
                    .header("X-Mail-Service-Key", apiKey)
                    .body(new SendEmailRequest(
                            recipients, subject, content.text(), content.html(), attachments))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IllegalStateException("No se pudo enviar el correo solicitado.", exception);
        }
    }

    static EmailContent crearContenido(String subject, String bodyHtml) {
        String text = HtmlUtils.htmlUnescape(bodyHtml
                .replaceAll("(?i)<br\\s*/?>|</(?:p|div|h[1-6]|tr|td)>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " "))
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        String html = """
                <!doctype html>
                <html lang="es">
                <body style="margin:0;padding:32px 12px;background:#eef1f5;font-family:Arial,Helvetica,sans-serif;color:#172235;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="border-collapse:collapse;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:620px;border:1px solid #e3eaf0;border-radius:20px;background:#ffffff;overflow:hidden;">
                        <tr><td style="padding:25px 32px;background:#172235;border-bottom:4px solid #f59a23;color:#ffffff;">
                          <p style="margin:0;color:#f59a23;font-size:12px;font-weight:bold;letter-spacing:2px;">DIGITRANS</p>
                          <p style="margin:7px 0 0;color:#d9e2ef;font-size:12px;letter-spacing:1px;">TU CIUDAD, A TU ALCANCE</p>
                          <h1 style="margin:12px 0 0;font-size:21px;line-height:1.35;color:#ffffff;">%s</h1>
                        </td></tr>
                        <tr><td style="padding:28px 32px;color:#455267;line-height:1.65;">
                          %s
                        </td></tr>
                        <tr><td style="padding:16px 32px;border-top:1px solid #edf1f4;color:#8490a0;font-size:12px;line-height:1.5;">
                          Mensaje automático · Digitrans · Cartagena de Indias, Colombia
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(subject), bodyHtml);
        return new EmailContent(text, html);
    }

    record EmailContent(String text, String html) {
    }

    private record EmailAttachment(String fileName, String contentType, String contentBase64, String contentId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record SendEmailRequest(List<String> to, String subject, String text, String html,
            List<EmailAttachment> attachments) {
    }
}
