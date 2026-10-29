package com.transcaribe.transcaribe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.transcaribe.transcaribe.Model.SistemaTransporte;
import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;

class PlataformaServiceCardUniquenessTest {

    private static final String CARD_NUMBER = "1234567890";
    private static final String USER_ID = "user-1";

    private UsuarioRepository usuarios;
    private PlataformaService service;

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioRepository.class);
        service = new PlataformaService(usuarios, mock(TransaccionService.class), mock(EmailService.class));
    }

    @Test
    void sameUserCanLinkSameCardNumberToAnotherTransportSystem() {
        Usuario user = new Usuario("persona@example.com", "", "Persona");
        user.setId(USER_ID);
        user.agregarTarjeta(CARD_NUMBER, BigDecimal.ZERO, SistemaTransporte.TRANSCARIBE);
        when(usuarios.findById(USER_ID)).thenReturn(Optional.of(user));
        when(usuarios.findByNumeroTarjetaEnListaYSistema(CARD_NUMBER, SistemaTransporte.TRANSMILENIO))
                .thenReturn(Optional.of(user));

        String result = service.agregarNuevaTarjetaConValidacion(
                USER_ID, CARD_NUMBER, SistemaTransporte.TRANSMILENIO);

        assertThat(result).isEqualTo("OK");
        assertThat(user.getTarjetas())
                .filteredOn(card -> card.getNumeroTarjeta().equals(CARD_NUMBER))
                .extracting(card -> card.getSistemaTransporte())
                .containsExactly(SistemaTransporte.TRANSCARIBE, SistemaTransporte.TRANSMILENIO);
        verify(usuarios).save(user);
    }

    @Test
    void anotherUserCannotClaimCardInSameTransportSystem() {
        Usuario owner = new Usuario("owner@example.com", "", "Owner");
        owner.setId("owner-1");
        Usuario requester = new Usuario("requester@example.com", "", "Requester");
        requester.setId(USER_ID);
        when(usuarios.findByNumeroTarjetaEnListaYSistema(CARD_NUMBER, SistemaTransporte.TRANSMILENIO))
                .thenReturn(Optional.of(owner));

        String result = service.agregarNuevaTarjetaConValidacion(
                USER_ID, CARD_NUMBER, SistemaTransporte.TRANSMILENIO);

        assertThat(result).isEqualTo("La tarjeta ya está registrada por otro usuario.");
        verify(usuarios, never()).save(requester);
    }

    @Test
    void sameUserCannotAddTheSameCardTwiceToOneTransportSystem() {
        Usuario user = new Usuario("persona@example.com", "", "Persona");
        user.setId(USER_ID);
        user.agregarTarjeta(CARD_NUMBER, BigDecimal.ZERO, SistemaTransporte.TRANSCARIBE);
        when(usuarios.findByNumeroTarjetaEnListaYSistema(CARD_NUMBER, SistemaTransporte.TRANSCARIBE))
                .thenReturn(Optional.of(user));
        when(usuarios.findById(USER_ID)).thenReturn(Optional.of(user));

        String result = service.agregarNuevaTarjetaConValidacion(
                USER_ID, CARD_NUMBER, SistemaTransporte.TRANSCARIBE);

        assertThat(result).isEqualTo("Ya tienes esta tarjeta agregada.");
        verify(usuarios, never()).save(user);
    }
}
