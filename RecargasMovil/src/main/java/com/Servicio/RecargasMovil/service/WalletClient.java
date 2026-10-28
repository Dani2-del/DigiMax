package com.Servicio.RecargasMovil.service;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class WalletClient {

    private final RestClient restClient;
    private final String billeteraUrl;

    public WalletClient(@Value("${app.billetera.base-url}") String billeteraUrl) {
        this.billeteraUrl = billeteraUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public Wallet getWallet(String token) {
        Wallet wallet;
        try {
            wallet = restClient.get()
                    .uri(billeteraUrl + "/api/wallet/me")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .retrieve()
                    .body(Wallet.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new WalletNotFoundException();
        }
        if (wallet == null) {
            throw new IllegalStateException("Billetera no devolvió la tarjeta universal.");
        }
        return wallet;
    }

    private static String bearer(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("No se encontró el token de Acceso para usar la tarjeta universal.");
        }
        return "Bearer " + token;
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

    public static class WalletNotFoundException extends RuntimeException {
        public WalletNotFoundException() {
            super("No tienes una tarjeta Digitrans creada.");
        }
    }

}
