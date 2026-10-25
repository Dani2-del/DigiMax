package com.Servicio.RecargasMovil.service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import com.Servicio.RecargasMovil.model.RecargaMovil;

@Service
public class RecargasExcelReportService {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    public void writeReport(
            Iterator<RecargaMovil> recargas,
            List<ReportColumn> columns,
            OutputStream output) throws IOException {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos una columna para el reporte.");
        }
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try (workbook) {
            Sheet sheet = workbook.createSheet("Recargas");
            sheet.createFreezePane(0, 1);
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row header = sheet.createRow(0);
            for (int column = 0; column < columns.size(); column++) {
                header.createCell(column).setCellValue(columns.get(column).header);
                header.getCell(column).setCellStyle(headerStyle);
            }

            int rowNumber = 1;
            while (recargas.hasNext()) {
                RecargaMovil recharge = recargas.next();
                Row row = sheet.createRow(rowNumber++);
                for (int column = 0; column < columns.size(); column++) {
                    Object value = columns.get(column).value(recharge);
                    if (value instanceof Number number) {
                        row.createCell(column).setCellValue(number.doubleValue());
                    } else {
                        setText(row, column, value == null ? null : value.toString());
                    }
                }
            }

            for (int column = 0; column < columns.size(); column++) {
                sheet.setColumnWidth(column, columns.get(column).width * 256);
            }
            workbook.write(output);
        } finally {
            workbook.dispose();
        }
    }

    public enum ReportColumn {
        OPERATION("operacion", "Operación", 24, RecargaMovil::getOperacionId),
        USER("usuario", "Usuario", 24, RecargaMovil::getUsuarioId),
        PHONE("telefono", "Teléfono", 18, RecargaMovil::getTelefono),
        OPERATOR("operador", "Operador", 20, RecargaMovil::getOperador),
        TYPE("tipo", "Tipo", 18, RecargasExcelReportService::tipoRecarga),
        CONCEPT("concepto", "Concepto", 32, RecargaMovil::getPaquete),
        AMOUNT("monto", "Monto (COP)", 18,
                recharge -> recharge.getMonto() == null ? null : recharge.getMonto().doubleValue()),
        STATUS("estado", "Estado", 18, RecargaMovil::getEstado),
        DATE("fecha", "Fecha", 22, recharge -> recharge.getFecha() == null
                ? null : recharge.getFecha().format(DATE_TIME_FORMAT));

        private final String key;
        private final String header;
        private final int width;
        private final java.util.function.Function<RecargaMovil, Object> valueProvider;

        ReportColumn(
                String key,
                String header,
                int width,
                java.util.function.Function<RecargaMovil, Object> valueProvider) {
            this.key = key;
            this.header = header;
            this.width = width;
            this.valueProvider = valueProvider;
        }

        private Object value(RecargaMovil recharge) {
            return valueProvider.apply(recharge);
        }

        public static List<ReportColumn> fromKeys(List<String> keys) {
            if (keys == null || keys.isEmpty()) return List.of();
            List<ReportColumn> selected = new ArrayList<>();
            for (ReportColumn column : values()) {
                if (keys.contains(column.key)) selected.add(column);
            }
            return List.copyOf(selected);
        }
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.VIOLET.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static void setText(Row row, int column, String value) {
        row.createCell(column).setCellValue(value == null ? "" : value);
    }

    private static String tipoRecarga(RecargaMovil recharge) {
        return recharge.getPaquete() != null
                && recharge.getPaquete().toLowerCase(java.util.Locale.ROOT).startsWith("recarga de saldo")
                ? "Recarga de saldo"
                : "Paquete";
    }
}
