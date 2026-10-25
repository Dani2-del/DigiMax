package com.Servicio.RecargasMovil.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import com.Servicio.RecargasMovil.model.RecargaMovil;

class RecargasExcelReportServiceTest {

    @Test
    void createsReportWithRechargeFieldsAndNumericAmounts() throws Exception {
        RecargaMovil recharge = new RecargaMovil();
        recharge.setOperacionId("operation-123");
        recharge.setUsuarioId("user-456");
        recharge.setTelefono("3001234567");
        recharge.setOperador("Claro");
        recharge.setPaquete("Recarga de saldo");
        recharge.setMonto(new BigDecimal("25000"));
        recharge.setEstado(RecargaMovil.STATUS_SIMULATED);
        recharge.setFecha(LocalDateTime.of(2026, 10, 25, 15, 30));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new RecargasExcelReportService().writeReport(
                List.of(recharge).iterator(),
                RecargasExcelReportService.ReportColumn.fromKeys(List.of(
                        "operacion", "usuario", "telefono", "operador", "tipo",
                        "concepto", "monto", "estado", "fecha")),
                output);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheet("Recargas");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Operación");
            assertThat(sheet.getRow(0).getCell(6).getStringCellValue()).isEqualTo("Monto (COP)");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("operation-123");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("Recarga de saldo");
            assertThat(sheet.getRow(1).getCell(6).getNumericCellValue()).isEqualTo(25000);
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo("25/10/2026 15:30");
        }
    }

    @Test
    void writesOnlySelectedColumnsInStableOrder() throws Exception {
        RecargaMovil recharge = new RecargaMovil();
        recharge.setOperacionId("operation-123");
        recharge.setEstado(RecargaMovil.STATUS_SIMULATED);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new RecargasExcelReportService().writeReport(
                List.of(recharge).iterator(),
                RecargasExcelReportService.ReportColumn.fromKeys(List.of("estado", "operacion")),
                output);

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(output.toByteArray()))) {
            var sheet = workbook.getSheet("Recargas");
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 2);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Operación");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Estado");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("operation-123");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo(RecargaMovil.STATUS_SIMULATED);
        }
    }
}
