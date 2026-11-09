package com.Servicio.Billetera.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Builds a small, dependency-free PDF receipt using the standard WinAnsi Helvetica fonts. */
public final class ReciboPdfGenerator {

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private ReciboPdfGenerator() {
    }

    public static byte[] generar(List<String> lineas) {
        byte[] content = contenido(lineas).getBytes(StandardCharsets.US_ASCII);
        List<byte[]> objects = List.of(
                ascii("<< /Type /Catalog /Pages 2 0 R >>"),
                ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                        + "/Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>"),
                ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"),
                ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"),
                streamObject(content));

        try {
            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            write(pdf, "%PDF-1.4\n% DigiTrans receipt\n");
            List<Integer> offsets = new ArrayList<>();
            for (int index = 0; index < objects.size(); index++) {
                offsets.add(pdf.size());
                write(pdf, (index + 1) + " 0 obj\n");
                pdf.write(objects.get(index));
                write(pdf, "\nendobj\n");
            }

            int crossReferenceOffset = pdf.size();
            write(pdf, "xref\n0 " + (objects.size() + 1) + "\n");
            write(pdf, "0000000000 65535 f \n");
            for (int offset : offsets) {
                write(pdf, String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offset));
            }
            write(pdf, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n"
                    + "startxref\n" + crossReferenceOffset + "\n%%EOF\n");
            return pdf.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar el recibo PDF.", exception);
        }
    }

    private static String contenido(List<String> lineas) {
        StringBuilder content = new StringBuilder();
        content.append("0.09 0.27 0.39 rg 0 770 595 72 re f\n");
        content.append("BT /F2 19 Tf 42 805 Td <")
                .append(hex(lineas.isEmpty() ? "DIGITRANS" : lineas.get(0)))
                .append("> Tj ET\n");
        content.append("0.35 0.43 0.49 RG 0.8 w 42 752 m 553 752 l S\n");

        int y = 720;
        int lineIndex = 0;
        for (int index = 1; index < lineas.size() && y > 55; index++) {
            String line = lineas.get(index) == null ? "" : lineas.get(index).trim();
            for (String part : wrap(line, 82)) {
                if (y <= 55) {
                    break;
                }
                boolean emphasized = lineIndex == 0;
                content.append("BT /").append(emphasized ? "F2 14" : "F1 11")
                        .append(" Tf 0.12 0.18 0.24 rg 48 ").append(y).append(" Td <")
                        .append(hex(part)).append("> Tj ET\n");
                y -= emphasized ? 31 : 22;
                lineIndex++;
            }
        }

        content.append("0.35 0.43 0.49 RG 0.8 w 42 47 m 553 47 l S\n")
                .append("BT /F1 8 Tf 48 30 Td <")
                .append(hex("DIGITRANS - Comprobante generado por el sistema"))
                .append("> Tj ET\n");
        return content.toString();
    }

    private static List<String> wrap(String text, int maxLength) {
        if (text.isEmpty()) {
            return List.of("");
        }
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (current.length() > 0 && current.length() + word.length() + 1 > maxLength) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static String hex(String value) {
        byte[] bytes = value.getBytes(WIN_ANSI);
        StringBuilder encoded = new StringBuilder(bytes.length * 2);
        for (byte valueByte : bytes) {
            encoded.append(String.format(java.util.Locale.ROOT, "%02X", valueByte & 0xff));
        }
        return encoded.toString();
    }

    private static byte[] streamObject(byte[] content) {
        ByteArrayOutputStream object = new ByteArrayOutputStream();
        write(object, "<< /Length " + content.length + " >>\nstream\n");
        try {
            object.write(content);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        write(object, "endstream");
        return object.toByteArray();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static void write(ByteArrayOutputStream output, String value) {
        try {
            output.write(ascii(value));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
