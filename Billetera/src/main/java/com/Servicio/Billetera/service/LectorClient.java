package com.Servicio.Billetera.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls Lector; RFID ownership data stays in that service. */
@Component
public class LectorClient {

    private final RestClient client;
    private final String baseUrl;
    private final String serviceKey;

    public LectorClient(@Value("${app.lector.base-url}") String baseUrl,
            @Value("${app.lector.api-key:}") String serviceKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl;
        this.serviceKey = serviceKey;
    }

    public Profile profile(String userId) {
        requireKey();
        return client.get().uri(baseUrl + "/internal/rfid/profiles/{userId}", userId)
                .header("X-Service-Key", serviceKey).retrieve().body(Profile.class);
    }

    public Profile claim(String userId) {
        requireKey();
        return client.post().uri(baseUrl + "/internal/rfid/profiles/{userId}/claim", userId)
                .header("X-Service-Key", serviceKey).header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body("{}").retrieve().body(Profile.class);
    }

    public void unlink(String userId) {
        requireKey();
        client.delete().uri(baseUrl + "/internal/rfid/profiles/{userId}", userId)
                .header("X-Service-Key", serviceKey).retrieve().toBodilessEntity();
    }

    private void requireKey() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new IllegalStateException("Configura LECTOR_API_KEY en Billetera y Lector.");
        }
    }

    public record Profile(String uid, String userId, boolean active, boolean available) { }
}
