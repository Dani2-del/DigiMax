package com.transcaribe.transcaribe.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.Usuario;

@Service
public class ReciboPdfService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public byte[] generarRecibo(Transaccion transaccion) throws IOException {
        Usuario usuario = transaccion.getUsuario();
        String fecha = transaccion.getFecha() == null
                ? "No disponible"
                : DATE_FORMAT.format(transaccion.getFecha());
        NumberFormat formatoMonto = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO"));
        formatoMonto.setMinimumFractionDigits(2);
        formatoMonto.setMaximumFractionDigits(2);

        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margin = 54;
                float y = page.getMediaBox().getHeight() - 72;

                String sistema = transaccion.getSistemaTransporte().getNombre().toUpperCase(Locale.ROOT);
                writeLine(content, PDType1Font.HELVETICA_BOLD, 20, margin, y, sistema);
                y -= 32;
                writeLine(content, PDType1Font.HELVETICA_BOLD, 15, margin, y, "RECIBO DE TRANSACCION");
                y -= 34;
                writeLine(content, PDType1Font.HELVETICA, 11, margin, y,
                        "Numero de transaccion: " + safeText(transaccion.getId()));
                y -= 22;
                writeLine(content, PDType1Font.HELVETICA, 11, margin, y,
                        "Fecha y hora registrada: " + fecha);
                y -= 36;
                writeLine(content, PDType1Font.HELVETICA_BOLD, 12, margin, y, "DATOS DEL USUARIO");
                y -= 22;
                writeLine(content, PDType1Font.HELVETICA, 11, margin, y,
                        "Nombre: " + safeText(usuario == null ? null : usuario.getNombre()));
                y -= 20;
                writeLine(content, PDType1Font.HELVETICA, 11, margin, y,
                        "Correo: " + safeText(usuario == null ? null : usuario.getCorreo()));
                y -= 36;
                writeLine(content, PDType1Font.HELVETICA_BOLD, 12, margin, y, "DETALLE");
                y -= 22;
                writeLine(content, PDType1Font.HELVETICA, 11, margin, y,
                        "Tipo: " + safeText(transaccion.getTipo()).replaceAll("\\[\\d{6,}\\]", "[****]"));
                y -= 20;
                writeLine(content, PDType1Font.HELVETICA, 11, margin, y,
                        "Cantidad de pasajes: " + (transaccion.getCantidadPasajes() == null
                                ? "No aplica"
                                : transaccion.getCantidadPasajes()));
                y -= 20;
                writeLine(content, PDType1Font.HELVETICA, 11, margin, y,
                        "Metodo de pago: " + safeText(transaccion.getMetodoPago()));
                y -= 20;
                writeLine(content, PDType1Font.HELVETICA, 11, margin, y,
                        "Cuenta PSE: " + safeText(transaccion.getCuentaPse()));
                y -= 20;
                writeLine(content, PDType1Font.HELVETICA, 11, margin, y,
                        "Tarjeta de transporte: " + safeText(transaccion.getTarjetaTranscaribe()));
                y -= 30;
                writeLine(content, PDType1Font.HELVETICA_BOLD, 14, margin, y,
                        "Monto: COP $" + formatoMonto.format(transaccion.getMonto()));
                y -= 48;
                writeLine(content, PDType1Font.HELVETICA_OBLIQUE, 9, margin, y,
                        "Comprobante informativo de una transaccion registrada en " + sistema + ".");
            }

            document.save(output);
            return output.toByteArray();
        }
    }

    private void writeLine(PDPageContentStream content, PDType1Font font, int size,
            float x, float y, String text) throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "No disponible";
        }

        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.replaceAll("[^\\x20-\\x7E]", "?");
    }
}
