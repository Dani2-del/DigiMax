package com.Servicio.Lector.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IdentityDirectoryClient {

    private final RestClient client;
    private final String accesoUrl;

    public IdentityDirectoryClient(@Value("${app.acceso.base-url:http://localhost:8080}") String accesoUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.accesoUrl = accesoUrl;
    }

    public Map<String, String> names(List<String> userIds, String token) {
        if (userIds == null || userIds.isEmpty() || token == null || token.isBlank()) return Map.of();
        List<String> distinctIds = userIds.stream().distinct().toList();
        java.util.Map<String, String> names = new java.util.LinkedHashMap<>();
        for (int start = 0; start < distinctIds.size(); start += 200) {
            List<String> batch = distinctIds.subList(start, Math.min(start + 200, distinctIds.size()));
            List<Identity> identities = client.post()
                    .uri(accesoUrl + "/api/auth/transport-identities")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .body(batch)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Identity>>() { });
            if (identities != null) {
                identities.stream().filter(identity -> identity.id() != null)
                        .forEach(identity -> names.put(identity.id(),
                                identity.name() == null || identity.name().isBlank()
                                        ? identity.id() : identity.name()));
            }
        }
        return names;
    }

    private record Identity(String id, String name) { }
}
