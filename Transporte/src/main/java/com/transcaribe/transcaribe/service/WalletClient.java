package com.transcaribe.transcaribe.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class WalletClient {

    public static final String UNIVERSAL_CARD_OPTION = "UNIVERSAL";

    private final RestClient restClient;
    private final String billeteraUrl;
    private final AccessIdentityClient accessIdentityClient;

    public WalletClient(
            @Value("${app.billetera.base-url:http://localhost:8082}") String billeteraUrl,
            AccessIdentityClient accessIdentityClient) {
        this.billeteraUrl = billeteraUrl;
        this.accessIdentityClient = accessIdentityClient;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public Wallet debit(String usuarioId, BigDecimal monto, String operacionId, String descripcion) {
        String token = tokenForUser(usuarioId);
        try {
            Wallet wallet = restClient.post()
                    .uri(billeteraUrl + "/api/wallet/me/debits")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(new DebitRequest(operacionId, monto, descripcion))
                    .retrieve()
                    .body(Wallet.class);
            if (wallet == null) {
                throw new IllegalStateException("Billetera no devolvió el saldo actualizado.");
            }
            return wallet;
        } catch (HttpClientErrorException.Conflict exception) {
            throw new InsufficientWalletBalanceException();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new WalletNotFoundException();
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode().value() == 423) {
                throw new WalletFrozenException();
            }
            throw exception;
        }
    }

    public Wallet getWallet(String usuarioId) {
        return findWallet(usuarioId)
                .orElseThrow(() -> new WalletNotFoundException());
    }

    public Optional<Wallet> findWallet(String usuarioId) {
        String token = tokenForUser(usuarioId);
        Wallet wallet;
        try {
            wallet = restClient.get()
                    .uri(billeteraUrl + "/api/wallet/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(Wallet.class);
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        }
        if (wallet == null) {
            throw new IllegalStateException("Billetera no devolvió la tarjeta universal.");
        }
        return Optional.of(wallet);
    }

    private String tokenForUser(String usuarioId) {
        AccessUser identity = accessIdentityClient.currentUser();
        if (identity == null || !usuarioId.equals(identity.id())) {
            throw new AccessDeniedException("La tarjeta universal solo puede pagar viajes de su titular.");
        }
        HttpServletRequest request = currentRequest();
        String token = (String) request.getAttribute(AccessIdentityClient.TOKEN_ATTRIBUTE);
        if (!StringUtils.hasText(token)) {
            throw new AccessDeniedException("Se requiere la sesión autenticada de Acceso.");
        }
        return token;
    }

    private static HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new AccessDeniedException("No hay una solicitud autenticada disponible.");
        }
        return attributes.getRequest();
    }

    public record Wallet(
            String userId, String cardNumber, BigDecimal balance, String status, String design) {
        public Wallet(String userId, String cardNumber, BigDecimal balance) {
            this(userId, cardNumber, balance, "ACTIVA", "OCEANO");
        }

        public boolean isFrozen() {
            return "CONGELADA".equals(status);
        }
    }

    private record DebitRequest(String operationId, BigDecimal amount, String description) {
    }

    public static class InsufficientWalletBalanceException extends RuntimeException {
        public InsufficientWalletBalanceException() {
            super("Saldo insuficiente en la tarjeta universal.");
        }
    }

    public static class WalletNotFoundException extends RuntimeException {
        public WalletNotFoundException() {
            super("Este usuario todavía no tiene una tarjeta Digitrans.");
        }
    }

    public static class WalletFrozenException extends RuntimeException {
        public WalletFrozenException() {
            super("Tu tarjeta Digitrans está congelada. Descongélala desde Billetera para volver a pagar.");
        }
    }
}
