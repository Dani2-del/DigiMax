package com.Servicio.Billetera.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.Servicio.Billetera.model.BilleteraUniversal;
import com.Servicio.Billetera.model.DisenoTarjeta;
import com.Servicio.Billetera.model.EstadoTarjeta;
import com.Servicio.Billetera.model.MovimientoBilletera;
import com.Servicio.Billetera.repository.BilleteraUniversalRepository;

@ExtendWith(MockitoExtension.class)
class BilleteraServiceTest {

    @Mock
    private BilleteraUniversalRepository billeteras;

    @Mock
    private MongoOperations mongoOperations;

    @Mock
    private PagosClient pagosClient;

    private BilleteraService service;

    @BeforeEach
    void setUp() {
        service = new BilleteraService(billeteras, mongoOperations, pagosClient);
    }

    @Test
    void returnsExistingWallet() {
        BilleteraUniversal wallet = wallet("user-1", "2500");
        when(billeteras.findById("user-1")).thenReturn(Optional.of(wallet));

        assertThat(service.obtener("user-1")).isSameAs(wallet);
    }

    @Test
    void movementClassifiesWalletTopUpsAsRecharge() {
        MovimientoBilletera movement = new MovimientoBilletera(
                "operation-1", "ABONO", "Carga simulada con PSE",
                new BigDecimal("20000"), null);

        assertThat(movement.getServicio()).isEqualTo("Billetera");
        assertThat(movement.getCategoria()).isEqualTo("RECARGA");

        MovimientoBilletera pending = topUpMovement("operation-2", "ABONO_PENDIENTE", "PENDIENTE");
        assertThat(pending.getCategoria()).isEqualTo("PENDIENTE");
        assertThat(pending.getServicio()).isEqualTo("Billetera");
    }

    @Test
    void movementInfersServiceForExistingTransportAndRechargeRecords() {
        MovimientoBilletera transport = new MovimientoBilletera(
                "operation-1", "CONSUMO", "Pasaje TransMilenio: ruta M86",
                new BigDecimal("3000"), null);
        MovimientoBilletera recharge = new MovimientoBilletera(
                "operation-2", "CONSUMO", "Paquete Claro para 3001234567",
                new BigDecimal("5000"), null);

        assertThat(transport.getServicio()).isEqualTo("TransMilenio");
        assertThat(transport.getCategoria()).isEqualTo("PAGO");
        assertThat(recharge.getServicio()).isEqualTo("Paquetes y recargas");
    }

    @Test
    void checkingForWalletDoesNotCreateOne() {
        when(billeteras.findById("user-1")).thenReturn(Optional.empty());

        assertThat(service.buscar("user-1")).isEmpty();
        verify(billeteras, times(0)).insert(any(BilleteraUniversal.class));
    }

    @Test
    void deactivatedWalletIsHiddenFromItsOwnerUntilReissue() {
        BilleteraUniversal wallet = wallet("user-1", "2500");
        wallet.setEstado(EstadoTarjeta.DESACTIVADA);
        when(billeteras.findById("user-1")).thenReturn(Optional.of(wallet));

        assertThat(service.buscar("user-1")).isEmpty();
        assertThatThrownBy(() -> service.obtener("user-1"))
                .isInstanceOf(BilleteraService.WalletNotFoundException.class);
    }

    @Test
    void existingWalletIsHiddenUntilUserAcceptsAndThenKeepsItsBalance() {
        BilleteraUniversal existing = wallet("user-1", "2500");
        existing.setActivadaPorUsuario(false);
        when(billeteras.findById("user-1")).thenReturn(Optional.of(existing));
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class)))
                .thenAnswer(invocation -> {
                    existing.setActivadaPorUsuario(true);
                    return existing;
                });

        assertThat(service.buscar("user-1")).isEmpty();
        assertThat(service.crear("user-1")).isSameAs(existing);

        assertThat(existing.isActivadaPorUsuario()).isTrue();
        assertThat(existing.getSaldo()).isEqualByComparingTo("2500");
        verify(billeteras, times(0)).insert(any(BilleteraUniversal.class));
    }

    @Test
    void savesUserActivationWhenAtomicUpdateDoesNotReturnWallet() {
        BilleteraUniversal existing = wallet("user-1", "2500");
        existing.setActivadaPorUsuario(false);
        when(billeteras.findById("user-1")).thenReturn(Optional.of(existing));
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(null);
        when(billeteras.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));

        BilleteraUniversal activated = service.crear("user-1");

        assertThat(activated.isActivadaPorUsuario()).isTrue();
        assertThat(activated.getSaldo()).isEqualByComparingTo("2500");
        verify(billeteras).save(existing);
    }

    @Test
    void explicitCreationMarksNewWalletAsAccepted() {
        when(billeteras.findById("user-1")).thenReturn(Optional.empty());
        when(billeteras.insert(any(BilleteraUniversal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BilleteraUniversal created = service.crear("user-1");

        assertThat(created.isActivadaPorUsuario()).isTrue();
        assertThat(created.getNumeroTarjeta()).startsWith("UNI-");
    }

    @Test
    void readingMissingWalletDoesNotProvisionIt() {
        when(billeteras.findById("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener("user-1"))
                .isInstanceOf(BilleteraService.WalletNotFoundException.class);
        verify(billeteras, times(0)).insert(any(BilleteraUniversal.class));
    }

    @Test
    void concurrentExplicitCreationReturnsExistingWalletInsteadOfCreatingAnother() {
        BilleteraUniversal existing = wallet("user-1", "0");
        AtomicInteger lookupCount = new AtomicInteger();
        when(billeteras.findById("user-1")).thenAnswer(invocation ->
                lookupCount.getAndIncrement() == 0 ? Optional.empty() : Optional.of(existing));
        when(billeteras.insert(any(BilleteraUniversal.class)))
                .thenThrow(new DuplicateKeyException("duplicate user wallet"));

        assertThat(service.crear("user-1")).isSameAs(existing);

        verify(billeteras, times(1)).insert(any(BilleteraUniversal.class));
    }

    @Test
    void deactivationArchivesBalanceAndKeepsItInTheWallet() {
        BilleteraUniversal active = wallet("user-1", "8750");
        BilleteraUniversal deactivated = wallet("user-1", "8750");
        deactivated.setEstado(EstadoTarjeta.DESACTIVADA);
        BilleteraUniversal archivedWallet = wallet("user-1", "8750");
        archivedWallet.setEstado(EstadoTarjeta.DESACTIVADA);
        when(billeteras.findById("user-1")).thenReturn(Optional.of(active));
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class)))
                .thenReturn(deactivated)
                .thenReturn(archivedWallet);

        BilleteraUniversal result = service.desactivar("user-1");

        assertThat(result.getEstado()).isEqualTo(EstadoTarjeta.DESACTIVADA);
        assertThat(result.getSaldo()).isEqualByComparingTo("8750");
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoOperations, times(2)).findAndModify(any(Query.class), updates.capture(),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class));
        Object archivedCardValue = updates.getAllValues().get(1)
                .getUpdateObject().get("$push", Document.class).get("tarjetasDesactivadas");
        assertThat(archivedCardValue).isInstanceOf(BilleteraUniversal.TarjetaDesactivada.class);
        BilleteraUniversal.TarjetaDesactivada archivedCard =
                (BilleteraUniversal.TarjetaDesactivada) archivedCardValue;
        assertThat(archivedCard.getNumeroTarjeta()).isEqualTo(active.getNumeroTarjeta());
        assertThat(archivedCard.getSaldoAlDesactivar())
                .isEqualByComparingTo("8750");
    }

    @Test
    void creatingAfterDeactivationIssuesANewNumberAndCarriesTheSameBalance() {
        BilleteraUniversal archivedWallet = wallet("user-1", "8750");
        archivedWallet.setEstado(EstadoTarjeta.DESACTIVADA);
        BilleteraUniversal.TarjetaDesactivada oldCard = new BilleteraUniversal.TarjetaDesactivada();
        oldCard.setNumeroTarjeta(archivedWallet.getNumeroTarjeta());
        oldCard.setSaldoAlDesactivar(new BigDecimal("8750"));
        oldCard.setDiseno(DisenoTarjeta.COBRE);
        archivedWallet.setTarjetasDesactivadas(List.of(oldCard));
        BilleteraUniversal reissuedWallet = wallet("user-1", "8750");
        reissuedWallet.setEstado(EstadoTarjeta.ACTIVA);
        reissuedWallet.setNumeroTarjeta("UNI-NEW0000000");
        oldCard.setSaldoTransferido(true);
        reissuedWallet.setTarjetasDesactivadas(List.of(oldCard));
        when(billeteras.findById("user-1")).thenReturn(Optional.of(archivedWallet));
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(reissuedWallet);

        BilleteraUniversal reissued = service.crear("user-1");

        assertThat(reissued.getNumeroTarjeta()).isEqualTo("UNI-NEW0000000");
        assertThat(reissued.getEstado()).isEqualTo(EstadoTarjeta.ACTIVA);
        assertThat(reissued.getSaldo()).isEqualByComparingTo("8750");
        assertThat(reissued.getTarjetasDesactivadas()).singleElement()
                .extracting(BilleteraUniversal.TarjetaDesactivada::isSaldoTransferido)
                .isEqualTo(true);
    }

    @Test
    void frozenWalletRejectsDebitsAndTopUpsBeforeChangingBalance() {
        BilleteraUniversal frozen = wallet("user-1", "2500");
        frozen.setEstado(EstadoTarjeta.CONGELADA);
        when(billeteras.findById("user-1")).thenReturn(Optional.of(frozen));

        assertThatThrownBy(() -> service.debitar(
                "user-1", new BigDecimal("100"), "operation-1", "Pasaje"))
                .isInstanceOf(BilleteraService.WalletFrozenException.class);
        assertThatThrownBy(() -> service.iniciarCargaSimulada(
                "user-1", "persona@example.com", new BigDecimal("100"), "operation-2", "PSE"))
                .isInstanceOf(BilleteraService.WalletFrozenException.class);
        verify(pagosClient, never()).iniciar(anyString(), any(), anyString(), anyString());
        verify(mongoOperations, times(0)).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class));
    }

    @Test
    void moderatorBalanceAdjustmentStoresActorAndReasonAsMovement() {
        when(billeteras.findById("user-1")).thenReturn(Optional.of(wallet("user-1", "2500")));
        BilleteraUniversal updated = wallet("user-1", "3500");
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(updated);

        BilleteraUniversal result = service.ajustarSaldoAdministrativamente(
                "user-1", "moderador@digitrans.test", new BigDecimal("1000.00"),
                true, "Corrección de recarga reportada", "admin-operation-1");

        assertThat(result.getSaldo()).isEqualByComparingTo("3500");
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoOperations).findAndModify(any(Query.class), update.capture(),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class));
        Document increment = update.getValue().getUpdateObject().get("$inc", Document.class);
        assertThat(increment.get("saldo", BigDecimal.class)).isEqualByComparingTo("1000.00");

        MovimientoBilletera auditMovement = new MovimientoBilletera(
                "admin-operation-1", "AJUSTE_ADMIN",
                "Ajuste por moderador@digitrans.test: Corrección de recarga reportada",
                new BigDecimal("1000.00"), null);
        assertThat(auditMovement.getCategoria()).isEqualTo("AJUSTE");
        assertThat(auditMovement.getServicio()).isEqualTo("Soporte Billetera");
    }

    @Test
    void moderatorCannotWithdrawMoreThanTheAvailableBalance() {
        when(billeteras.findById("user-1")).thenReturn(Optional.of(wallet("user-1", "500")));
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(null);

        assertThatThrownBy(() -> service.ajustarSaldoAdministrativamente(
                "user-1", "moderador@digitrans.test", new BigDecimal("1000.00"),
                false, "Corrección solicitada por soporte", "admin-operation-2"))
                .isInstanceOf(SaldoInsuficienteException.class);
    }

    @Test
    void moderatorStatusChangesIncludeAnAuditableSupportMovement() {
        BilleteraUniversal active = wallet("user-1", "2500");
        when(billeteras.findById("user-1")).thenReturn(Optional.of(active));
        BilleteraUniversal frozen = wallet("user-1", "2500");
        frozen.setEstado(EstadoTarjeta.CONGELADA);
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(frozen);

        BilleteraUniversal result = service.cambiarEstadoAdministrativamente(
                "user-1", EstadoTarjeta.CONGELADA, "moderador@digitrans.test", "Reporte de posible fraude");

        assertThat(result.getEstado()).isEqualTo(EstadoTarjeta.CONGELADA);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoOperations).findAndModify(any(Query.class), update.capture(),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class));
        assertThat(update.getValue().getUpdateObject().toString()).contains("CONGELADA");

        MovimientoBilletera auditMovement = new MovimientoBilletera(
                "status-operation-1", "CAMBIO_ESTADO",
                "Estado cambiado a CONGELADA por moderador@digitrans.test: Reporte de posible fraude",
                BigDecimal.ZERO, null);
        assertThat(auditMovement.getCategoria()).isEqualTo("SOPORTE");
        assertThat(auditMovement.getServicio()).isEqualTo("Soporte Billetera");
    }

    @Test
    void simulatedTopUpStartsAsPendingWithoutIncreasingBalance() {
        when(billeteras.findById("user-1")).thenReturn(Optional.of(wallet("user-1", "2500")));
        BilleteraUniversal reserved = wallet("user-1", "2500");
        reserved.setMovimientos(List.of(topUpMovement(
                "operation-1", "ABONO_PENDIENTE", "INICIANDO")));
        BilleteraUniversal pending = wallet("user-1", "2500");
        pending.setMovimientos(List.of(topUpMovement(
                "operation-1", "ABONO_PENDIENTE", "PENDIENTE", "payment-1")));
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class)))
                .thenReturn(reserved)
                .thenReturn(pending);
        when(pagosClient.iniciar(anyString(), eq(new BigDecimal("20000.00")),
                anyString(), eq("persona@example.com")))
                .thenReturn(new PagosClient.ResultadoPago("payment-1", "PENDIENTE", null));

        BilleteraUniversal result = service.iniciarCargaSimulada(
                "user-1", "persona@example.com", new BigDecimal("20000.00"),
                "operation-1", "PSE");

        assertThat(result.getSaldo()).isEqualByComparingTo("2500");
        assertThat(result.getMovimientos()).singleElement()
                .satisfies(movement -> {
                    assertThat(movement.getTipo()).isEqualTo("ABONO_PENDIENTE");
                    assertThat(movement.getIdPagoProveedor()).isEqualTo("payment-1");
                    assertThat(movement.getEstadoPago()).isEqualTo("PENDIENTE");
                    assertThat(movement.getMetodoPago()).isEqualTo("PSE");
                });
        verify(pagosClient).iniciar(anyString(), eq(new BigDecimal("20000.00")),
                anyString(), eq("persona@example.com"));
    }

    @Test
    void creditsBalanceOnlyAfterPaymentIsApprovedAndDoesItAtomically() {
        BilleteraUniversal pending = wallet("user-1", "2500");
        pending.setMovimientos(List.of(topUpMovement(
                "operation-1", "ABONO_PENDIENTE", "PENDIENTE", "payment-1")));
        BilleteraUniversal approved = wallet("user-1", "22500");
        approved.setMovimientos(List.of(topUpMovement(
                "operation-1", "ABONO", "APROBADO", "payment-1")));
        when(billeteras.findById("user-1")).thenReturn(Optional.of(pending));
        when(pagosClient.consultar("payment-1"))
                .thenReturn(new PagosClient.ResultadoPago("payment-1", "APROBADO", null));
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(approved);

        BilleteraUniversal result = service.revisarCarga(
                "user-1", "operation-1", "persona@example.com");

        assertThat(result.getSaldo()).isEqualByComparingTo("22500");
        assertThat(result.getMovimientos()).singleElement()
                .satisfies(movement -> {
                    assertThat(movement.getTipo()).isEqualTo("ABONO");
                    assertThat(movement.getEstadoPago()).isEqualTo("APROBADO");
                });
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoOperations).findAndModify(any(Query.class), update.capture(),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class));
        assertThat(update.getValue().getUpdateObject().get("$inc", Document.class)
                .get("saldo", BigDecimal.class)).isEqualByComparingTo("20000.00");
    }

    @Test
    void rejectedPaymentDoesNotIncreaseBalance() {
        BilleteraUniversal pending = wallet("user-1", "2500");
        pending.setMovimientos(List.of(topUpMovement(
                "operation-1", "ABONO_PENDIENTE", "PENDIENTE", "payment-1")));
        BilleteraUniversal rejected = wallet("user-1", "2500");
        rejected.setMovimientos(List.of(topUpMovement(
                "operation-1", "ABONO_RECHAZADO", "RECHAZADO", "payment-1")));
        when(billeteras.findById("user-1")).thenReturn(Optional.of(pending));
        when(pagosClient.consultar("payment-1"))
                .thenReturn(new PagosClient.ResultadoPago("payment-1", "RECHAZADO", null));
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(rejected);

        BilleteraUniversal result = service.revisarCarga(
                "user-1", "operation-1", "persona@example.com");

        assertThat(result.getSaldo()).isEqualByComparingTo("2500");
        assertThat(result.getMovimientos()).singleElement()
                .satisfies(movement -> assertThat(movement.getTipo()).isEqualTo("ABONO_RECHAZADO"));
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoOperations).findAndModify(any(Query.class), update.capture(),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class));
        assertThat(update.getValue().getUpdateObject()).doesNotContainKey("$inc");
    }

    @Test
    void reconcilesAnOldPendingLoadWithoutBrowserInteraction() {
        BilleteraUniversal pending = wallet("user-1", "2500");
        MovimientoBilletera movement = topUpMovement(
                "operation-1", "ABONO_PENDIENTE", "PENDIENTE", "payment-1");
        movement.setFecha(LocalDateTime.now().minusSeconds(20));
        pending.setMovimientos(List.of(movement));
        BilleteraUniversal approved = wallet("user-1", "22500");
        approved.setMovimientos(List.of(topUpMovement(
                "operation-1", "ABONO", "APROBADO", "payment-1")));
        when(mongoOperations.find(any(Query.class), eq(BilleteraUniversal.class)))
                .thenReturn(List.of(pending));
        when(pagosClient.consultar("payment-1"))
                .thenReturn(new PagosClient.ResultadoPago("payment-1", "APROBADO", null));
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(approved);

        service.revisarCargasPendientesVencidas();

        verify(pagosClient).consultar("payment-1");
        verify(mongoOperations).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class));
    }

    @Test
    void replacesProviderPaymentLostAfterPagosRestart() {
        BilleteraUniversal pending = wallet("user-1", "2500");
        MovimientoBilletera movement = topUpMovement(
                "operation-1", "ABONO_PENDIENTE", "PENDIENTE", "old-payment");
        movement.setCorreoPagador("persona@example.com");
        pending.setMovimientos(List.of(movement));
        BilleteraUniversal refreshed = wallet("user-1", "2500");
        MovimientoBilletera newMovement = topUpMovement(
                "operation-1", "ABONO_PENDIENTE", "PENDIENTE", "new-payment");
        refreshed.setMovimientos(List.of(newMovement));
        when(billeteras.findById("user-1")).thenReturn(Optional.of(pending));
        when(pagosClient.consultar("old-payment")).thenThrow(new RestClientResponseException(
                "missing", 404, "Not Found", null, null, null));
        when(pagosClient.iniciar(anyString(), any(), anyString(),
                eq("persona@example.com"))).thenReturn(
                        new PagosClient.ResultadoPago("new-payment", "PENDIENTE", null));
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(refreshed);

        BilleteraUniversal result = service.revisarCarga(
                "user-1", "operation-1", "persona@example.com");

        assertThat(result).isSameAs(refreshed);
        verify(pagosClient).iniciar(anyString(), any(), anyString(),
                eq("persona@example.com"));
    }

    @Test
    void retryingTheSameApprovedLoadDoesNotCreditTwice() {
        BilleteraUniversal approved = wallet("user-1", "22500");
        approved.setMovimientos(List.of(topUpMovement(
                "operation-1", "ABONO", "APROBADO", "payment-1")));
        when(billeteras.findById("user-1")).thenReturn(Optional.of(approved));

        BilleteraUniversal result = service.iniciarCargaSimulada(
                "user-1", "persona@example.com", new BigDecimal("20000.00"),
                "operation-1", "PSE");

        assertThat(result).isSameAs(approved);
        verify(pagosClient, never()).iniciar(anyString(), any(), anyString(), anyString());
        verify(pagosClient, never()).consultar(anyString());
        verify(mongoOperations, never()).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class));
    }

    @Test
    void rejectsUnsupportedSimulatedTopUpMethod() {
        assertThatThrownBy(() -> service.iniciarCargaSimulada(
                "user-1", "persona@example.com", new BigDecimal("20000"), "operation-1", "TRANSFERENCIA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona PSE o tarjeta crédito/débito.");
        verify(pagosClient, never()).iniciar(anyString(), any(), anyString(), anyString());
    }

    @Test
    void debitsAtomicallyAndReturnsUpdatedWallet() {
        BilleteraUniversal updated = wallet("user-1", "500");
        when(billeteras.findById("user-1")).thenReturn(Optional.of(wallet("user-1", "2500")));
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(updated);

        BilleteraUniversal result = service.debitar(
                "user-1", new BigDecimal("2000"), "operation-1", "Paquete móvil");

        assertThat(result.getSaldo()).isEqualByComparingTo("500");
        verify(mongoOperations).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class));
    }

    @Test
    void rejectsDebitWhenBalanceIsInsufficient() {
        when(billeteras.findById("user-1")).thenReturn(Optional.of(wallet("user-1", "500")));
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(null);

        assertThatThrownBy(() -> service.debitar(
                "user-1", new BigDecimal("2000"), "operation-1", "Paquete móvil"))
                .isInstanceOf(SaldoInsuficienteException.class);
    }

    @Test
    void returnsSameWalletForRepeatedOperationId() {
        BilleteraUniversal wallet = wallet("user-1", "500");
        wallet.setMovimientos(List.of(new MovimientoBilletera(
                "operation-1", "CONSUMO", "Paquete móvil", new BigDecimal("2000"), null)));
        when(billeteras.findById("user-1")).thenReturn(Optional.of(wallet));
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(null);

        BilleteraUniversal result = service.debitar(
                "user-1", new BigDecimal("2000"), "operation-1", "Paquete móvil");

        assertThat(result).isSameAs(wallet);
    }

    private static MovimientoBilletera topUpMovement(
            String operationId, String type, String paymentState) {
        return topUpMovement(operationId, type, paymentState, null);
    }

    private static MovimientoBilletera topUpMovement(
            String operationId, String type, String paymentState, String paymentId) {
        MovimientoBilletera movement = new MovimientoBilletera(
                operationId, type, "Carga simulada de saldo con PSE",
                new BigDecimal("20000.00"), null);
        movement.setServicio("Billetera");
        movement.setMetodoPago("PSE");
        movement.setEstadoPago(paymentState);
        movement.setIdPagoProveedor(paymentId);
        return movement;
    }

    private static BilleteraUniversal wallet(String userId, String balance) {
        BilleteraUniversal wallet = new BilleteraUniversal();
        wallet.setUsuarioId(userId);
        wallet.setNumeroTarjeta("UNI-TEST000000");
        wallet.setSaldo(new BigDecimal(balance));
        wallet.setActivadaPorUsuario(true);
        return wallet;
    }
}
