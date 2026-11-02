package com.Servicio.Billetera.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.core.ParameterizedTypeReference;
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

    public List<UserSummary> findByIds(List<String> userIds, String token) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        if (userIds.size() > 200 || userIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("La consulta por lote requiere entre 1 y 200 identidades válidas.");
        }
        List<IdentityResponse> identities = restClient.post()
                .uri(accesoUrl + "/api/auth/transport-identities")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(userIds)
                .retrieve()
                .body(new ParameterizedTypeReference<List<IdentityResponse>>() { });
        if (identities == null) {
            throw new IllegalStateException("Acceso devolvió una respuesta vacía al consultar identidades.");
        }
        return identities.stream()
                .map(identity -> new UserSummary(identity.id(), identity.name(), identity.email()))
                .toList();
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

    private record IdentityResponse(
            String id, String email, String name, String role, boolean active,
            boolean verified, java.time.LocalDateTime registeredAt,
            String phone, String serviceAssignment) {
    }

    public record UserPage(List<UserSummary> content, long totalElements, int totalPages, int page, int size) {
    }
}
