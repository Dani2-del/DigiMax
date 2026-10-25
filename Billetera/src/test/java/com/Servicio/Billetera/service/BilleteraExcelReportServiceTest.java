package com.Servicio.Billetera.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.Servicio.Billetera.model.MovimientoBilletera;
import com.Servicio.Billetera.service.BilleteraExcelReportService.MovementReportRow;
import com.Servicio.Billetera.service.BilleteraExcelReportService.ReportColumn;

class BilleteraExcelReportServiceTest {

    private final BilleteraExcelReportService service = new BilleteraExcelReportService();

    @Test
    void createsWorkbookWithOnlySelectedColumnsAndTypedValues() throws Exception {
        MovimientoBilletera movement = new MovimientoBilletera(
                "op-123", "ABONO", "Recarga de billetera",
                new BigDecimal("2500.50"), LocalDateTime.of(2026, 10, 25, 10, 30));

        byte[] report = service.createReport(
                List.of(new MovementReportRow("user-1", movement)),
                List.of(ReportColumn.USER, ReportColumn.AMOUNT, ReportColumn.DATE));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(report))) {
            var sheet = workbook.getSheet("Movimientos");
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 3);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Usuario (ID)");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Monto (COP)");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Fecha");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("user-1");
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(2500.50);
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue())
                    .isEqualTo("25/10/2026 10:30");
        }
    }
}
