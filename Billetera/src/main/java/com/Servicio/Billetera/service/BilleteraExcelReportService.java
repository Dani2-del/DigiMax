package com.Servicio.Billetera.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import com.Servicio.Billetera.model.MovimientoBilletera;

@Service
public class BilleteraExcelReportService {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public byte[] createReport(
            List<MovementReportRow> rows, List<ReportColumn> columns) throws IOException {
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos una columna para el reporte.");
        }
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try (workbook; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Movimientos");
            sheet.createFreezePane(0, 1);
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row header = sheet.createRow(0);
            for (int index = 0; index < columns.size(); index++) {
                header.createCell(index).setCellValue(columns.get(index).header);
                header.getCell(index).setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (MovementReportRow reportRow : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
                    Object value = columns.get(columnIndex).value.apply(reportRow);
                    if (value instanceof Number number) {
                        row.createCell(columnIndex).setCellValue(number.doubleValue());
                    } else {
                        row.createCell(columnIndex).setCellValue(value == null ? "" : value.toString());
                    }
                }
            }
            for (int index = 0; index < columns.size(); index++) {
                sheet.setColumnWidth(index, columns.get(index).width * 256);
            }
            workbook.write(output);
            return output.toByteArray();
        } finally {
            workbook.dispose();
        }
    }

    public enum ReportColumn {
        USER("usuario", "Usuario (ID)", 24, MovementReportRow::userId),
        OPERATION("operacion", "Operación", 28, row -> row.movement().getOperacionId()),
        TYPE("tipo", "Tipo", 20, row -> row.movement().getTipo()),
        DESCRIPTION("descripcion", "Descripción", 42, row -> row.movement().getDescripcion()),
        SERVICE("servicio", "Servicio", 24, row -> row.movement().getServicio()),
        AMOUNT("monto", "Monto (COP)", 20, row -> row.movement().getMonto() == null
                ? null : row.movement().getMonto().doubleValue()),
        DATE("fecha", "Fecha", 22, row -> formatDate(row.movement().getFecha()));

        private final String key;
        private final String header;
        private final int width;
        private final Function<MovementReportRow, Object> value;

        ReportColumn(String key, String header, int width, Function<MovementReportRow, Object> value) {
            this.key = key;
            this.header = header;
            this.width = width;
            this.value = value;
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

    public record MovementReportRow(String userId, MovimientoBilletera movement) {
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static String formatDate(LocalDateTime date) {
        return date == null ? "" : date.format(DATE_TIME_FORMAT);
    }
}
