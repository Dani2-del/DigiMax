package com.transcaribe.transcaribe.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.transcaribe.transcaribe.Model.Bus;
import com.transcaribe.transcaribe.Model.RutaTransmetro;
import com.transcaribe.transcaribe.Model.RutaTransmilenio;
import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Transaccion;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.BusRepository;
import com.transcaribe.transcaribe.Repository.TransaccionRepository;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;
import com.transcaribe.transcaribe.Util.TarifaTranscaribe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusServiceUniversalPaymentTest {

    private static final String USER_ID = "user-1";
    private static final String OPERATION_ID = "ride-operation-1";

    @Mock
    private BusRepository busRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private WalletClient walletClient;

    @InjectMocks
    private BusService busService;

    @Test
    void universalCardDebitsSharedWalletAndPersistsTransportTransaction() {
        Usuario usuario = activeUser();
        Bus bus = new Bus("BUS-1", List.of("T100"));
        bus.setId("bus-1");
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(usuario));
        when(busRepository.findById("bus-1")).thenReturn(Optional.of(bus));
        when(transaccionRepository.findByUsuarioIdAndOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.empty());
        when(walletClient.debit(eq(USER_ID), eq(BigDecimal.valueOf(3900)), eq(OPERATION_ID), anyString()))
                .thenReturn(new WalletClient.Wallet(USER_ID, "UNI-123", BigDecimal.valueOf(6100)));

        String result = busService.pagarPasajeConTarjetaEspecifica(
                USER_ID, "bus-1", WalletClient.UNIVERSAL_CARD_OPTION,
                SistemaTransporte.TRANSCARIBE, OPERATION_ID);

        assertTrue(result.contains("Cobro exitoso con tarjeta universal"));
        ArgumentCaptor<Transaccion> transaction = ArgumentCaptor.forClass(Transaccion.class);
        verify(transaccionRepository).save(transaction.capture());
        assertEquals(OPERATION_ID, transaction.getValue().getOperationId());
        assertEquals(WalletClient.UNIVERSAL_CARD_OPTION, transaction.getValue().getTarjetaTranscaribe());
        verify(walletClient).debit(eq(USER_ID), eq(BigDecimal.valueOf(3900)), eq(OPERATION_ID), anyString());
    }

    @Test
    void retryingSameUniversalPaymentDoesNotDebitWalletAgain() {
        Usuario usuario = activeUser();
        Bus bus = new Bus("BUS-1", List.of("T100"));
        bus.setId("bus-1");
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(usuario));
        when(busRepository.findById("bus-1")).thenReturn(Optional.of(bus));
        when(transaccionRepository.findByUsuarioIdAndOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.empty(), Optional.empty());
        when(walletClient.debit(eq(USER_ID), eq(BigDecimal.valueOf(3900)), eq(OPERATION_ID), anyString()))
                .thenReturn(new WalletClient.Wallet(USER_ID, "UNI-123", BigDecimal.valueOf(6100)));

        busService.pagarPasajeConTarjetaEspecifica(
                USER_ID, "bus-1", WalletClient.UNIVERSAL_CARD_OPTION,
                SistemaTransporte.TRANSCARIBE, OPERATION_ID);
        ArgumentCaptor<Transaccion> transaction = ArgumentCaptor.forClass(Transaccion.class);
        verify(transaccionRepository).save(transaction.capture());
        when(transaccionRepository.findByUsuarioIdAndOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.of(transaction.getValue()));

        String retryResult = busService.pagarPasajeConTarjetaEspecifica(
                USER_ID, "bus-1", WalletClient.UNIVERSAL_CARD_OPTION,
                SistemaTransporte.TRANSCARIBE, OPERATION_ID);

        assertEquals("Pago ya procesado; no se realizó un nuevo cobro.", retryResult);
        verify(walletClient, times(1))
                .debit(eq(USER_ID), eq(BigDecimal.valueOf(3900)), eq(OPERATION_ID), anyString());
        verify(transaccionRepository, times(1)).save(any(Transaccion.class));
    }

    @Test
    void universalCardCanPayTransmilenioFare() {
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(transaccionRepository.findByUsuarioIdAndOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.empty());
        when(walletClient.debit(eq(USER_ID), eq(BigDecimal.valueOf(
                TarifaTranscaribe.paraSistema(SistemaTransporte.TRANSMILENIO))),
                eq(OPERATION_ID), anyString()))
                .thenReturn(new WalletClient.Wallet(USER_ID, "UNI-123", BigDecimal.valueOf(6450)));
        RutaTransmilenio ruta = new RutaTransmilenio(
                "route-1", "K10", "Ruta", "K10", "Portal", "Centro",
                "05:00-23:00", "05:00-23:00", "05:00-23:00");

        String result = busService.simularViajeTransmilenio(
                USER_ID, WalletClient.UNIVERSAL_CARD_OPTION, ruta, OPERATION_ID);

        assertTrue(result.contains("Cobro exitoso con tarjeta universal"));
        verify(walletClient).debit(eq(USER_ID), eq(BigDecimal.valueOf(
                TarifaTranscaribe.paraSistema(SistemaTransporte.TRANSMILENIO))),
                eq(OPERATION_ID), anyString());
    }

    @Test
    void universalCardCanPayTransmetroFare() {
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(transaccionRepository.findByUsuarioIdAndOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.empty());
        when(walletClient.debit(eq(USER_ID), eq(BigDecimal.valueOf(
                TarifaTranscaribe.paraSistema(SistemaTransporte.TRANSMETRO))),
                eq(OPERATION_ID), anyString()))
                .thenReturn(new WalletClient.Wallet(USER_ID, "UNI-123", BigDecimal.valueOf(6300)));
        RutaTransmetro ruta = new RutaTransmetro(
                "T1", "TRONCAL", true, List.of("Norte", "Centro"), List.of("06:00"));

        String result = busService.simularViajeTransmetro(
                USER_ID, WalletClient.UNIVERSAL_CARD_OPTION, ruta, OPERATION_ID);

        assertTrue(result.contains("Cobro exitoso con tarjeta universal"));
        verify(walletClient).debit(eq(USER_ID), eq(BigDecimal.valueOf(
                TarifaTranscaribe.paraSistema(SistemaTransporte.TRANSMETRO))),
                eq(OPERATION_ID), anyString());
    }

    @Test
    void localTransportCardStillPaysWithoutCallingUniversalWallet() {
        Usuario usuario = activeUser();
        usuario.agregarTarjeta("LOCAL-123", BigDecimal.valueOf(5000), SistemaTransporte.TRANSCARIBE);
        Bus bus = new Bus("BUS-1", List.of("T100"));
        bus.setId("bus-1");
        when(usuarioRepository.findById(USER_ID)).thenReturn(Optional.of(usuario));
        when(busRepository.findById("bus-1")).thenReturn(Optional.of(bus));
        when(transaccionRepository.findByUsuarioIdAndOperationId(USER_ID, OPERATION_ID))
                .thenReturn(Optional.empty());

        String result = busService.pagarPasajeConTarjetaEspecifica(
                USER_ID, "bus-1", "LOCAL-123", SistemaTransporte.TRANSCARIBE, OPERATION_ID);

        assertTrue(result.contains("Cobro exitoso"));
        assertEquals(BigDecimal.valueOf(1100), usuario.getTarjetas().get(0).getSaldo());
        verify(walletClient, times(0)).debit(anyString(), any(), anyString(), anyString());
    }

    private static Usuario activeUser() {
        Usuario usuario = new Usuario("user@example.com", "hash", "Test User");
        usuario.setId(USER_ID);
        usuario.setActivo(true);
        return usuario;
    }
}
