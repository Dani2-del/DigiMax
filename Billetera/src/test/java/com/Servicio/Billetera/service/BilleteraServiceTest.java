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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.Servicio.Billetera.model.BilleteraUniversal;
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
    void existingWalletIsHiddenUntilUserAcceptsAndThenKeepsItsBalance() {
        BilleteraUniversal existing = wallet("user-1", "2500");
        existing.setActivadaPorUsuario(false);
        when(billeteras.findById("user-1")).thenReturn(Optional.of(existing));
        when(billeteras.save(existing)).thenReturn(existing);

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
