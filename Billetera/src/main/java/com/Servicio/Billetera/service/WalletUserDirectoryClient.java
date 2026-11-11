package com.Servicio.Billetera.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

@Component
public class WalletUserDirectoryClient {

    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final RestClient roleUpdateClient;
    private final String accesoUrl;

    public WalletUserDirectoryClient(@Value("${ACCESO_INTERNAL_URL:${app.acceso.base-url}}") String accesoUrl) {
        this.restClient = createClient(Duration.ofSeconds(3));
        this.roleUpdateClient = createRoleUpdateClient();
        this.accesoUrl = accesoUrl;
    }

    private static RestClient createClient(Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private static RestClient createRoleUpdateClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().requestFactory(requestFactory).build();
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
                .map(identity -> new UserSummary(
                        identity.id(), identity.name(), identity.email(),
                        identity.role(), identity.serviceAssignment(), identity.active()))
                .toList();
    }

    public UserSummary updateRole(
            UserSummary user, String role, String serviceAssignment, String token) {
        return roleUpdateClient.patch()
                .uri(accesoUrl + "/api/admin/users/{id}", user.id())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(new UpdateUserRequest(
                        user.name(), user.email(), role, user.active(), serviceAssignment))
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

    public record UserSummary(
            String id, String name, String email, String role, String serviceAssignment, boolean active) {
        public UserSummary(String id, String name, String email, String role, String serviceAssignment) {
            this(id, name, email, role, serviceAssignment, true);
        }

        public UserSummary(String id, String name, String email) {
            this(id, name, email, "ROLE_USER", null, true);
        }
    }

    public record UpdateUserRequest(
            String name, String email, String role, boolean active, String serviceAssignment) {
    }

    private record IdentityResponse(
            String id, String email, String name, String role, boolean active,
            boolean verified, java.time.LocalDateTime registeredAt,
            String phone, String serviceAssignment) {
    }

    public record UserPage(List<UserSummary> content, long totalElements, int totalPages, int page, int size) {
    }
}
