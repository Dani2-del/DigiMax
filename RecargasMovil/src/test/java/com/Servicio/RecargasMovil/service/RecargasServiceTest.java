package com.Servicio.RecargasMovil.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.Servicio.RecargasMovil.model.LineaMovil;
import com.Servicio.RecargasMovil.model.OperadorMovil;
import com.Servicio.RecargasMovil.model.CategoriaPaquete;
import com.Servicio.RecargasMovil.model.PaqueteMovil;
import com.Servicio.RecargasMovil.model.RecargaMovil;
import com.Servicio.RecargasMovil.repository.LineaMovilRepository;
import com.Servicio.RecargasMovil.repository.OperadorMovilRepository;
import com.Servicio.RecargasMovil.repository.PaqueteMovilRepository;
import com.Servicio.RecargasMovil.repository.RecargaMovilRepository;

@ExtendWith(MockitoExtension.class)
class RecargasServiceTest {

    @Mock
    private OperadorMovilRepository operadores;

    @Mock
    private PaqueteMovilRepository paquetes;

    @Mock
    private LineaMovilRepository lineas;

    @Mock
    private RecargaMovilRepository recargas;

    @Mock
    private WalletClient walletClient;

    private RecargasService service;

    @BeforeEach
    void setUp() {
        service = new RecargasService(operadores, paquetes, lineas, recargas, walletClient);
    }

    @Test
    void debitsUniversalWalletAndRecordsSimulatedRechargeForOwnedLine() {
        OperadorMovil operator = operator("operator-1");
        LineaMovil line = line("line-1", "user-1", "operator-1");
        PaqueteMovil mobilePackage = mobilePackage("package-1", "operator-1", "Datos");
        when(recargas.findById("operation-1")).thenReturn(Optional.empty());
        when(lineas.findById("line-1")).thenReturn(Optional.of(line));
        when(operadores.findById("operator-1")).thenReturn(Optional.of(operator));
        when(paquetes.findById("package-1")).thenReturn(Optional.of(mobilePackage));
        when(recargas.insert(any(RecargaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(walletClient.debit("token", "operation-1", new BigDecimal("5000.00"),
                "Paquete Operador Demo para ****4567"))
                .thenReturn(new WalletClient.Wallet("user-1", "UNI-123", new BigDecimal("10000")));
        when(recargas.save(any(RecargaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecargaMovil result = service.solicitarRecarga(
                "user-1", "line-1", "package-1", "operation-1", "token");

        assertThat(result.getEstado()).isEqualTo(RecargaMovil.STATUS_SIMULATED);
        assertThat(result.getMonto()).isEqualByComparingTo("5000");
        assertThat(result.getTelefono()).isEqualTo("+573001234567");
        verify(walletClient).debit("token", "operation-1", new BigDecimal("5000.00"),
                "Paquete Operador Demo para ****4567");
    }

    @Test
    void rejectsLineOwnedByAnotherUserBeforeChargingWallet() {
        when(recargas.findById("operation-1")).thenReturn(Optional.empty());
        when(lineas.findById("line-1")).thenReturn(Optional.of(line("line-1", "other-user", "operator-1")));

        assertThatThrownBy(() -> service.solicitarRecarga(
                "user-1", "line-1", "package-1", "operation-1", "token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Selecciona una línea tuya.");

        verify(walletClient, never()).debit(any(), any(), any(), any());
        verify(recargas, never()).insert(any(RecargaMovil.class));
    }

    @Test
    void returnsCompletedRechargeWithoutChargingWalletAgain() {
        RecargaMovil completed = new RecargaMovil();
        completed.setOperacionId("operation-1");
        completed.setUsuarioId("user-1");
        completed.setEstado(RecargaMovil.STATUS_SIMULATED);
        when(recargas.findById("operation-1")).thenReturn(Optional.of(completed));

        RecargaMovil result = service.solicitarRecarga(
                "user-1", "line-1", "package-1", "operation-1", "token");

        assertThat(result).isSameAs(completed);
        verify(walletClient, never()).debit(any(), any(), any(), any());
    }

    @Test
    void recordsRejectedStateWhenUniversalBalanceIsInsufficient() {
        OperadorMovil operator = operator("operator-1");
        LineaMovil line = line("line-1", "user-1", "operator-1");
        PaqueteMovil mobilePackage = mobilePackage("package-1", "operator-1", "Datos");
        when(recargas.findById("operation-1")).thenReturn(Optional.empty());
        when(lineas.findById("line-1")).thenReturn(Optional.of(line));
        when(operadores.findById("operator-1")).thenReturn(Optional.of(operator));
        when(paquetes.findById("package-1")).thenReturn(Optional.of(mobilePackage));
        when(recargas.insert(any(RecargaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(walletClient.debit("token", "operation-1", new BigDecimal("5000.00"),
                "Paquete Operador Demo para ****4567"))
                .thenThrow(new InsufficientWalletBalanceException());
        when(recargas.save(any(RecargaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.solicitarRecarga(
                "user-1", "line-1", "package-1", "operation-1", "token"))
                .isInstanceOf(InsufficientWalletBalanceException.class);

        verify(recargas).save(org.mockito.ArgumentMatchers.argThat(
                recharge -> RecargaMovil.STATUS_REJECTED.equals(recharge.getEstado())));
    }

    @Test
    void simulatesExitoMoneyRechargeAndDebitsTheChosenAmount() {
        OperadorMovil exito = operator("exito-1");
        exito.setNombre("Móvil Éxito");
        LineaMovil line = line("line-1", "user-1", "exito-1");
        when(recargas.findById("operation-2")).thenReturn(Optional.empty());
        when(operadores.findById("exito-1")).thenReturn(Optional.of(exito));
        when(lineas.findById("line-1")).thenReturn(Optional.of(line));
        when(recargas.insert(any(RecargaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(walletClient.debit("token", "operation-2", new BigDecimal("25000.00"),
                "Paquete Móvil Éxito para ****4567"))
                .thenReturn(new WalletClient.Wallet("user-1", "UNI-123", new BigDecimal("10000")));
        when(recargas.save(any(RecargaMovil.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RecargaMovil result = service.solicitarRecargaDinero(
                "user-1", "exito-1", "line-1", new BigDecimal("25000"), "operation-2", "token");

        assertThat(result.getEstado()).isEqualTo(RecargaMovil.STATUS_SIMULATED);
        assertThat(result.getMonto()).isEqualByComparingTo("25000");
        assertThat(result.getPaquete()).isEqualTo("Recarga de saldo $25000");
        verify(walletClient).debit("token", "operation-2", new BigDecimal("25000.00"),
                "Paquete Móvil Éxito para ****4567");
    }

    @Test
    void rejectsInvalidCustomRechargeAmountsBeforeDebitingWallet() {
        assertThatThrownBy(() -> service.solicitarRecargaDinero(
                "user-1", "exito-1", "line-1", new BigDecimal("1500"), "operation-3", "token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("incrementos de $1.000");

        verify(walletClient, never()).debit(any(), any(), any(), any());
        verify(recargas, never()).insert(any(RecargaMovil.class));
    }

    @Test
    void rejectsPostpaidPlanFromPrepaidWalletRechargeFlow() {
        OperadorMovil claro = operator("claro-1");
        claro.setNombre("Claro");
        LineaMovil line = line("line-1", "user-1", "claro-1");
        PaqueteMovil postpaidPlan = mobilePackage("postpaid-1", "claro-1", "Conectados");
        postpaidPlan.setCategoria(CategoriaPaquete.POSTPAGO);
        when(recargas.findById("operation-4")).thenReturn(Optional.empty());
        when(lineas.findById("line-1")).thenReturn(Optional.of(line));
        when(operadores.findById("claro-1")).thenReturn(Optional.of(claro));
        when(paquetes.findById("postpaid-1")).thenReturn(Optional.of(postpaidPlan));

        assertThatThrownBy(() -> service.solicitarRecarga(
                "user-1", "line-1", "postpaid-1", "operation-4", "token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("oferta prepago");

        verify(walletClient, never()).debit(any(), any(), any(), any());
        verify(recargas, never()).insert(any(RecargaMovil.class));
    }

    private static OperadorMovil operator(String id) {
        OperadorMovil operator = new OperadorMovil();
        operator.setId(id);
        operator.setNombre("Operador Demo");
        operator.setActivo(true);
        return operator;
    }

    private static LineaMovil line(String id, String userId, String operatorId) {
        LineaMovil line = new LineaMovil();
        line.setId(id);
        line.setUsuarioId(userId);
        line.setOperadorId(operatorId);
        line.setTelefono("+573001234567");
        return line;
    }

    private static PaqueteMovil mobilePackage(String id, String operatorId, String name) {
        PaqueteMovil mobilePackage = new PaqueteMovil();
        mobilePackage.setId(id);
        mobilePackage.setOperadorId(operatorId);
        mobilePackage.setNombre(name);
        mobilePackage.setPrecio(new BigDecimal("5000.00"));
        mobilePackage.setActivo(true);
        return mobilePackage;
    }
}
