package com.Servicio.RecargasMovil.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

import com.Servicio.RecargasMovil.security.AuthenticatedIdentity;
import com.Servicio.RecargasMovil.security.RemoteAccessAuthenticationFilter;
import com.Servicio.RecargasMovil.service.RecargasService;
import com.Servicio.RecargasMovil.service.WalletClient;

class ServiceLandingControllerTest {

    private RecargasService recargasService;
    private WalletClient walletClient;
    private ServiceLandingController controller;

    @BeforeEach
    void setUp() {
        recargasService = mock(RecargasService.class);
        walletClient = mock(WalletClient.class);
        controller = new ServiceLandingController(
                recargasService, walletClient, "http://localhost:8080", "http://localhost:8082");
        when(recargasService.operadoresActivos()).thenReturn(List.of());
        when(recargasService.paquetesActivos()).thenReturn(List.of());
        when(recargasService.lineasDelUsuario("user-1")).thenReturn(List.of());
        when(recargasService.historialDelUsuario("user-1")).thenReturn(List.of());
    }

    @Test
    void missingWalletPromptsUserToGoCreateDigitransCard() {
        when(walletClient.getWallet("token"))
                .thenThrow(new WalletClient.WalletNotFoundException());
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletRequest request = requestWithToken();
        CsrfToken csrfToken = mock(CsrfToken.class);
        when(csrfToken.getToken()).thenReturn("csrf-token");
        request.setAttribute("_csrf", csrfToken);

        String view = controller.index(identity(), csrfToken, request, model);

        assertThat(view).isEqualTo("servicio-recargas");
        assertThat(model.getAttribute("walletMissing")).isEqualTo(true);
        assertThat(model.getAttribute("walletError")).isEqualTo(false);
        assertThat(model.getAttribute("billeteraUrl")).isEqualTo("http://localhost:8082");
        assertThat(model.getAttribute("_csrf")).isSameAs(csrfToken);
    }

    @Test
    void walletServerErrorsRemainErrorsInsteadOfShowingMissingCardPrompt() {
        when(walletClient.getWallet("token"))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.SERVICE_UNAVAILABLE, "Unavailable", null, null, null));
        ConcurrentModel model = new ConcurrentModel();
        MockHttpServletRequest request = requestWithToken();
        CsrfToken csrfToken = mock(CsrfToken.class);
        when(csrfToken.getToken()).thenReturn("csrf-token");
        request.setAttribute("_csrf", csrfToken);

        controller.index(identity(), csrfToken, request, model);

        assertThat(model.getAttribute("walletMissing")).isEqualTo(false);
        assertThat(model.getAttribute("walletError")).isEqualTo(true);
        assertThat(model.getAttribute("error")).asString().contains("HTTP 503");
    }

    @Test
    private static AuthenticatedIdentity identity() {
        return new AuthenticatedIdentity("user-1", "user@example.com", "User", "ROLE_USER");
    }

    private static MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RemoteAccessAuthenticationFilter.TOKEN_ATTRIBUTE, "token");
        return request;
    }
}
