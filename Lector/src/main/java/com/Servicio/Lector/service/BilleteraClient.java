package com.Servicio.Lector.service;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BilleteraClient {

    private final RestClient restClient;
    private final String billeteraUrl;
    private final String serviceKey;

    public BilleteraClient(
            @Value("${app.billetera.base-url}") String billeteraUrl,
            @Value("${app.billetera.api-key}") String serviceKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.billeteraUrl = billeteraUrl;
        this.serviceKey = serviceKey;
    }

    public DebitResult debit(String userId, BigDecimal amount, String operationId, String description) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("Configura LECTOR_API_KEY en Billetera y Lector.");
        }
        return restClient.post()
                .uri(billeteraUrl + "/internal/wallets/{userId}/debits", userId)
                .header("X-Service-Key", serviceKey)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(new DebitRequest(operationId, amount, description))
                .retrieve()
                .body(DebitResult.class);
    }

    public record DebitRequest(String operationId, BigDecimal amount, String description) { }

    public record DebitResult(
            String userId, String cardNumber, BigDecimal balance,
            String status, String design) { }
}
