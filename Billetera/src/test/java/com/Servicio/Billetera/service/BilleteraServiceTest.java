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
    void concurrentProvisioningReturnsExistingWalletInsteadOfCreatingAnother() {
        BilleteraUniversal existing = wallet("user-1", "0");
        AtomicInteger lookupCount = new AtomicInteger();
        when(billeteras.findById("user-1")).thenAnswer(invocation ->
                lookupCount.getAndIncrement() == 0 ? Optional.empty() : Optional.of(existing));
        when(billeteras.insert(any(BilleteraUniversal.class)))
                .thenThrow(new DuplicateKeyException("duplicate user wallet"));

        assertThat(service.obtener("user-1")).isSameAs(existing);

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
    void rejectsUnsupportedSimulatedTopUpMethod() {
        assertThatThrownBy(() -> service.abonarSimulado(
                "user-1", new BigDecimal("20000"), "operation-1", "TRANSFERENCIA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona PSE o tarjeta débito.");
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
        return wallet;
    }
}
