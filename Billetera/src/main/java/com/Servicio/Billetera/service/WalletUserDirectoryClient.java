package com.Servicio.Billetera.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WalletUserDirectoryClient {

    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final String accesoUrl;

    public WalletUserDirectoryClient(@Value("${app.acceso.base-url}") String accesoUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.accesoUrl = accesoUrl;
    }

    public UserSummary findById(String userId, String token) {
        return restClient.get()
                .uri(accesoUrl + "/api/admin/users/{id}", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(UserSummary.class);
    }

    public UserPage search(String query, int page, String token) {
        UserPage result = restClient.get()
                .uri(accesoUrl + "/api/admin/users?page={page}&size={size}&search={search}&sort=recent",
                        page, PAGE_SIZE, query)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(UserPage.class);
        if (result == null || result.content() == null) {
            throw new IllegalStateException("Acceso devolvió una lista de usuarios vacía.");
        }
        return result;
    }

    public record UserSummary(String id, String name, String email) {
    }

    public record UserPage(List<UserSummary> content, long totalElements, int totalPages, int page, int size) {
    }
}
