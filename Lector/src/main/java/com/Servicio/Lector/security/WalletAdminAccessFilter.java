package com.Servicio.Lector.security;

import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Restricts the reader UI and browser API to Billetera administrators and moderators. */
@Component
public class WalletAdminAccessFilter extends OncePerRequestFilter {

    private final RestClient accessClient;
    private final String accesoUrl;

    public WalletAdminAccessFilter(@Value("${app.acceso.base-url:http://localhost:8080}") String accesoUrl) {
        this.accesoUrl = accesoUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.accessClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/internal/") || path.startsWith("/css/")
                || path.startsWith("/js/") || path.startsWith("/images/")
                || path.startsWith("/error") || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = accessToken(request);
        if (token == null) {
            rejectUnauthenticated(request, response);
            return;
        }

        try {
            Identity identity = accessClient.get()
                    .uri(accesoUrl + "/api/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .body(Identity.class);
            if (identity == null || !identity.active() || !isWalletAdmin(identity)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden exception) {
            rejectUnauthenticated(request, response);
            return;
        } catch (ResourceAccessException exception) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "No se pudo validar el acceso con Acceso.");
            return;
        } catch (RestClientResponseException exception) {
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY,
                    "No se pudo validar la identidad con Acceso.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isWalletAdmin(Identity identity) {
        return "ROLE_ADMIN".equals(identity.role())
                || ("ROLE_MODERATOR".equals(identity.role())
                        && "BILLETERA".equals(identity.serviceAssignment()));
    }

    private void rejectUnauthenticated(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (request.getServletPath().startsWith("/api/")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            response.sendRedirect(accesoUrl + "/login");
        }
    }

    private static String accessToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")
                && authorization.length() > "Bearer ".length()) {
            return authorization.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("JWT_TOKEN".equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private record Identity(String role, boolean active, String serviceAssignment) { }
}
