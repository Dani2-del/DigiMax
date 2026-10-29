package com.transcaribe.transcaribe.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.net.http.HttpClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class AccessIdentityClient {

    public static final String TOKEN_ATTRIBUTE = AccessIdentityClient.class.getName() + ".token";
    public static final String USER_ATTRIBUTE = AccessIdentityClient.class.getName() + ".user";

    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final String accesoUrl;

    public AccessIdentityClient(@Value("${app.acceso.base-url:http://localhost:8080}") String accesoUrl) {
        this.accesoUrl = accesoUrl;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public AccessUser currentUser() {
        Object value = currentRequest().getAttribute(USER_ATTRIBUTE);
        return value instanceof AccessUser user ? user : null;
    }

    public AccessUser restClientGetMe(String token) {
        return restClient.get()
                .uri(accesoUrl + "/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(AccessUser.class);
    }

    public AccessUser findById(String id) {
        AccessUser current = currentUser();
        if (current != null && current.id().equals(id)) {
            return current;
        }
        try {
            return authorizedGet()
                    .uri(accesoUrl + "/api/admin/users/{id}", id)
                    .retrieve()
                    .body(AccessUser.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound exception) {
            return null;
        }
    }

    public AccessUser findByEmail(String email) {
        AccessUser current = currentUser();
        if (current != null && current.email().equalsIgnoreCase(email)) {
            return current;
        }
        java.net.URI uri = UriComponentsBuilder.fromUriString(accesoUrl)
                        .path("/api/admin/users/by-email")
                        .queryParam("email", email)
                        .build()
                        .encode()
                        .toUri();
        try {
            return authorizedGet()
                    .uri(uri)
                    .retrieve()
                    .body(AccessUser.class);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound exception) {
            return null;
        }
    }

    public List<AccessUser> findAll() {
        return listUsers("");
    }

    public List<AccessUser> search(String search) {
        return listUsers(search);
    }

    public List<AccessUser> findAdministrativeContacts() {
        List<AccessAdministrativeContact> contacts = authorizedGet()
                .uri(accesoUrl + "/api/auth/administrative-contacts")
                .retrieve()
                .body(new ParameterizedTypeReference<List<AccessAdministrativeContact>>() {});
        if (contacts == null) {
            throw new IllegalStateException("Acceso devolvió una lista de contactos vacía.");
        }
        return contacts.stream()
                .map(contact -> new AccessUser(
                        contact.id(), null, contact.email(), contact.role(), true, true, null, null, null))
                .toList();
    }

    public List<AccessUser> findTransportUsers(List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> !StringUtils.hasText(id))) {
            throw new IllegalArgumentException("La consulta debe incluir IDs de usuario válidos.");
        }
        List<String> uniqueIds = ids.stream().distinct().toList();
        List<AccessUser> users = new ArrayList<>();
        for (int start = 0; start < uniqueIds.size(); start += 200) {
            List<String> batch = uniqueIds.subList(start, Math.min(start + 200, uniqueIds.size()));
            List<AccessUser> batchUsers = authorizedPost()
                    .uri(accesoUrl + "/api/auth/transport-identities")
                    .body(batch)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AccessUser>>() {});
            if (batchUsers == null) {
                throw new IllegalStateException("Acceso devolvió una lista de identidades vacía.");
            }
            users.addAll(batchUsers);
        }
        return users;
    }

    public AccessUser updateUser(
            String id, String name, String email, String role, boolean active, String serviceAssignment) {
        return authorizedPatch()
                .uri(accesoUrl + "/api/admin/users/{id}", id)
                .body(new UpdateUserRequest(name, email, role, active, serviceAssignment))
                .retrieve()
                .body(AccessUser.class);
    }

    private List<AccessUser> listUsers(String search) {
        List<AccessUser> users = new ArrayList<>();
        AccessUserPage page;
        int pageNumber = 0;
        do {
            page = authorizedGet()
                    .uri(buildListUri(pageNumber, search))
                    .retrieve()
                    .body(AccessUserPage.class);
            if (page == null || page.content() == null) {
                throw new IllegalStateException("Acceso devolvió una página de usuarios vacía.");
            }
            users.addAll(page.content());
            pageNumber++;
        } while (pageNumber < page.totalPages());
        return users;
    }

    private java.net.URI buildListUri(int page, String search) {
        return UriComponentsBuilder.fromUriString(accesoUrl)
                .path("/api/admin/users")
                .queryParam("page", page)
                .queryParam("size", PAGE_SIZE)
                .queryParam("search", search)
                .build()
                .encode()
                .toUri();
    }

    private RestClient.RequestHeadersUriSpec<?> authorizedGet() {
        String token = (String) currentRequest().getAttribute(TOKEN_ATTRIBUTE);
        if (!StringUtils.hasText(token)) {
            throw new AccessDeniedException(
                    "Se requiere un JWT de Acceso para consultar o modificar identidades centrales.");
        }
        RestClient.RequestHeadersUriSpec<?> request = restClient.get();
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private RestClient.RequestBodyUriSpec authorizedPatch() {
        String token = (String) currentRequest().getAttribute(TOKEN_ATTRIBUTE);
        if (!StringUtils.hasText(token)) {
            throw new AccessDeniedException(
                    "Se requiere un JWT de Acceso para consultar o modificar identidades centrales.");
        }
        RestClient.RequestBodyUriSpec request = restClient.patch();
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private RestClient.RequestBodyUriSpec authorizedPost() {
        String token = (String) currentRequest().getAttribute(TOKEN_ATTRIBUTE);
        if (!StringUtils.hasText(token)) {
            throw new AccessDeniedException(
                    "Se requiere un JWT de Acceso para consultar identidades centrales.");
        }
        RestClient.RequestBodyUriSpec request = restClient.post();
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            throw new AccessDeniedException("No hay una solicitud autenticada disponible para consultar Acceso.");
        }
        return attributes.getRequest();
    }

    private record UpdateUserRequest(
            String name, String email, String role, boolean active, String serviceAssignment) {
    }
}
