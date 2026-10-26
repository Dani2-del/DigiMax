package com.Servicio.Billetera.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.Servicio.Billetera.model.AccionVerificacionTarjeta;
import com.Servicio.Billetera.model.VerificacionTarjeta;
import com.Servicio.Billetera.repository.VerificacionTarjetaRepository;

@ExtendWith(MockitoExtension.class)
class VerificacionTarjetaServiceTest {

    @Mock
    private VerificacionTarjetaRepository verificaciones;

    @Mock
    private ServicioCorreoBilletera correo;

    private VerificacionTarjetaService service;

    @BeforeEach
    void setUp() {
        service = new VerificacionTarjetaService(verificaciones, correo);
        when(verificaciones.save(any(VerificacionTarjeta.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void sendsAndConsumesSingleUseSixDigitCreationCode() {
        AtomicReference<String> sentCode = new AtomicReference<>();
        when(verificaciones.findById("user-1")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            sentCode.set(invocation.getArgument(2));
            return null;
        }).when(correo).enviarCodigoTarjeta(
                eq("user@example.com"),
                eq("Danier"),
                any(String.class),
                eq(AccionVerificacionTarjeta.CREAR));

        service.iniciar("user-1", "user@example.com", "Danier", AccionVerificacionTarjeta.CREAR);
        ArgumentCaptor<VerificacionTarjeta> captor = ArgumentCaptor.forClass(VerificacionTarjeta.class);
        verify(verificaciones).save(captor.capture());
        VerificacionTarjeta saved = captor.getValue();
        assertThat(sentCode.get()).matches("\\d{6}");
        assertThat(saved.getCodigoHash()).isNotEqualTo(sentCode.get());
        assertThat(saved.getExpiraEn()).isAfter(saved.getSolicitadaEn());

        when(verificaciones.findById("user-1")).thenReturn(Optional.of(saved));
        assertThat(service.verificar("user-1", sentCode.get()))
                .isEqualTo(AccionVerificacionTarjeta.CREAR);
        assertThat(saved.isConsumida()).isTrue();
        verify(verificaciones, times(2)).save(saved);

        assertThatThrownBy(() -> service.verificar("user-1", sentCode.get()))
                .isInstanceOf(VerificacionTarjetaService.ErrorVerificacionTarjeta.class);
    }

    @Test
    void sendsSixDigitCodeForFreezingCard() {
        AtomicReference<String> sentCode = new AtomicReference<>();
        when(verificaciones.findById("user-1")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            sentCode.set(invocation.getArgument(2));
            return null;
        }).when(correo).enviarCodigoTarjeta(
                eq("user@example.com"),
                eq("Danier"),
                any(String.class),
                eq(AccionVerificacionTarjeta.CONGELAR));

        service.iniciar("user-1", "user@example.com", "Danier", AccionVerificacionTarjeta.CONGELAR);

        ArgumentCaptor<VerificacionTarjeta> captor = ArgumentCaptor.forClass(VerificacionTarjeta.class);
        verify(verificaciones).save(captor.capture());
        VerificacionTarjeta saved = captor.getValue();
        assertThat(sentCode.get()).matches("\\d{6}");
        assertThat(saved.getAccion()).isEqualTo(AccionVerificacionTarjeta.CONGELAR);
        when(verificaciones.findById("user-1")).thenReturn(Optional.of(saved));
        assertThat(service.verificar("user-1", sentCode.get()))
                .isEqualTo(AccionVerificacionTarjeta.CONGELAR);
    }

    @Test
    void rejectsIncorrectCodeAndIncrementsAttemptCount() {
        VerificacionTarjeta challenge = new VerificacionTarjeta();
        challenge.setUsuarioId("user-1");
        challenge.setAccion(AccionVerificacionTarjeta.DESACTIVAR);
        challenge.setCodigoHash("not-the-code");
        challenge.setSalt("salt");
        challenge.setExpiraEn(java.time.LocalDateTime.now().plusMinutes(10));
        when(verificaciones.findById("user-1")).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> service.verificar("user-1", "123456"))
                .isInstanceOf(VerificacionTarjetaService.ErrorVerificacionTarjeta.class)
                .hasMessage("El código ingresado no es válido.");
        assertThat(challenge.getIntentosFallidos()).isEqualTo(1);
        verify(verificaciones).save(challenge);
    }

    @Test
    void invalidatesCodeAfterFiveIncorrectAttempts() {
        VerificacionTarjeta challenge = new VerificacionTarjeta();
        challenge.setUsuarioId("user-1");
        challenge.setAccion(AccionVerificacionTarjeta.DESACTIVAR);
        challenge.setCodigoHash("not-the-code");
        challenge.setSalt("salt");
        challenge.setExpiraEn(java.time.LocalDateTime.now().plusMinutes(10));
        when(verificaciones.findById("user-1")).thenReturn(Optional.of(challenge));

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.verificar("user-1", "123456"))
                    .isInstanceOf(VerificacionTarjetaService.ErrorVerificacionTarjeta.class);
        }
        assertThatThrownBy(() -> service.verificar("user-1", "123456"))
                .isInstanceOf(VerificacionTarjetaService.ErrorVerificacionTarjeta.class)
                .hasMessage("Se agotaron los intentos. Solicita un nuevo código.");
        assertThat(challenge.getIntentosFallidos()).isEqualTo(5);
        verify(verificaciones, times(4)).save(challenge);
        verify(verificaciones).deleteById("user-1");
    }
}
