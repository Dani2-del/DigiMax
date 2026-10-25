package com.Servicio.Acceso.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TransportSitmClient {

    private final RestClient restClient;
    private final String transporteUrl;

    public TransportSitmClient(@Value("${app.services.transporte-url}") String transporteUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.transporteUrl = transporteUrl;
    }

    public SitmInfo getSitm(String accessToken) {
        SitmInfo info = restClient.get()
                .uri(transporteUrl + "/api/usuario/sitm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(SitmInfo.class);
        if (info == null) {
            throw new IllegalStateException("Transporte devolvió un perfil SITM vacío.");
        }
        return info;
    }

    public SitmInfo changeSitm(String accessToken, String sitm) {
        SitmInfo info = restClient.post()
                .uri(transporteUrl + "/api/usuario/sitm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .body(new SitmChangeRequest(sitm))
                .retrieve()
                .body(SitmInfo.class);
        if (info == null) {
            throw new IllegalStateException("Transporte devolvió un perfil SITM vacío.");
        }
        return info;
    }

    public record SitmInfo(String currentSitm) {
    }

    private record SitmChangeRequest(String sitm) {
    }
}
