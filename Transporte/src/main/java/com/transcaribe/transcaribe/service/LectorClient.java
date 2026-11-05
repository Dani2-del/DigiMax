package com.transcaribe.transcaribe.service;

import java.time.Duration;
import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Connects Transporte to the local RFID service without storing card UIDs here. */
@Component
public class LectorClient {

    private final RestClient client;
    private final String baseUrl;
    private final String serviceKey;

    public LectorClient(@Value("${app.lector.base-url:http://localhost:8083}") String baseUrl,
            @Value("${app.lector.api-key:}") String serviceKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    public RideResult charge(String uid, String userId, String operationId, String system, BigDecimal amount) {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("Configura LECTOR_API_KEY en Transporte y Lector.");
        }
        return client.post().uri(baseUrl + "/internal/rfid/lecturas")
                .header("X-Service-Key", serviceKey)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(new RideRequest(uid, userId, operationId, system, amount))
                .retrieve().body(RideResult.class);
    }

    public record RideRequest(String uid, String userId, String operationId, String sistema, BigDecimal monto) { }
    public record RideResult(String status, String message, String uid, String userId,
            String cardNumber, java.math.BigDecimal charged, java.math.BigDecimal balance) { }
}
