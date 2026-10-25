package com.Servicio.Acceso.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.Servicio.Acceso.service.GlobalOperationsService.GlobalTransaction;

class GlobalOperationsServiceTest {

    @Test
    void deduplicatesCrossServiceOperationAndPrefersServiceRecord() {
        GlobalTransaction walletMovement = transaction(
                "operation-1", "RECARGAS_MOVIL", "Billetera");
        GlobalTransaction recharge = transaction(
                "operation-1", "RECARGAS_MOVIL", "RecargasMovil");

        List<GlobalTransaction> unique =
                GlobalOperationsService.deduplicate(List.of(walletMovement, recharge));

        assertThat(unique).containsExactly(recharge);
    }

    @Test
    void retainsRecordsWithoutOperationIdsAsSeparateEvents() {
        GlobalTransaction first = transaction(null, "BILLETERA", "Billetera");
        GlobalTransaction second = transaction(null, "BILLETERA", "Billetera");

        assertThat(GlobalOperationsService.deduplicate(List.of(first, second)))
                .containsExactly(first, second);
    }

    private static GlobalTransaction transaction(String operationId, String service, String source) {
        return new GlobalTransaction(operationId, service, "CONSUMO", "Test",
                "user-1", "", "", new BigDecimal("100"), "COMPLETADA",
                LocalDateTime.of(2026, 10, 24, 12, 0), source);
    }
}
