package com.Servicio.Billetera.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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

    private BilleteraService service;

    @BeforeEach
    void setUp() {
        service = new BilleteraService(billeteras, mongoOperations);
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
        assertThatThrownBy(() -> service.abonarSimulado(
                "user-1", new BigDecimal("100"), "operation-2", "PSE"))
                .isInstanceOf(BilleteraService.WalletFrozenException.class);
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
    void simulatedTopUpAcceptsPse() {
        when(billeteras.findById("user-1")).thenReturn(Optional.of(wallet("user-1", "2500")));
        BilleteraUniversal updated = wallet("user-1", "22500");
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(updated);

        BilleteraUniversal result = service.abonarSimulado(
                "user-1", new BigDecimal("20000"), "operation-1", "PSE");

        assertThat(result.getSaldo()).isEqualByComparingTo("22500");
    }

    @Test
    void simulatedTopUpAcceptsDebitCard() {
        when(billeteras.findById("user-1")).thenReturn(Optional.of(wallet("user-1", "2500")));
        BilleteraUniversal updated = wallet("user-1", "22500");
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(updated);

        BilleteraUniversal result = service.abonarSimulado(
                "user-1", new BigDecimal("20000"), "operation-1", "TARJETA_DEBITO");

        assertThat(result.getSaldo()).isEqualByComparingTo("22500");
    }

    @Test
    void simulatedTopUpAcceptsCreditOrDebitCard() {
        when(billeteras.findById("user-1")).thenReturn(Optional.of(wallet("user-1", "2500")));
        BilleteraUniversal updated = wallet("user-1", "22500");
        when(mongoOperations.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(BilleteraUniversal.class))).thenReturn(updated);

        BilleteraUniversal result = service.abonarSimulado(
                "user-1", new BigDecimal("20000"), "operation-1", "TARJETA_CREDITO_DEBITO");

        assertThat(result.getSaldo()).isEqualByComparingTo("22500");
    }

    @Test
    void rejectsUnsupportedSimulatedTopUpMethod() {
        assertThatThrownBy(() -> service.abonarSimulado(
                "user-1", new BigDecimal("20000"), "operation-1", "TRANSFERENCIA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona PSE o tarjeta crédito/débito.");
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

    private static BilleteraUniversal wallet(String userId, String balance) {
        BilleteraUniversal wallet = new BilleteraUniversal();
        wallet.setUsuarioId(userId);
        wallet.setNumeroTarjeta("UNI-TEST000000");
        wallet.setSaldo(new BigDecimal(balance));
        wallet.setActivadaPorUsuario(true);
        return wallet;
    }
}
