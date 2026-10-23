package com.Servicio.RecargasMovil.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

public class RemoteAccessAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_ATTRIBUTE = RemoteAccessAuthenticationFilter.class.getName() + ".token";

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteAccessAuthenticationFilter.class);

    private final RestClient restClient = RestClient.create();
    private final String accesoUrl;
    private final boolean secureCookie;

    public RemoteAccessAuthenticationFilter(String accesoUrl, boolean secureCookie) {
        this.accesoUrl = accesoUrl;
        this.secureCookie = secureCookie;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/") || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = getToken(request);
        SecurityContextHolder.clearContext();
        if (token != null) {
            try {
                Identity identity = restClient.get()
                        .uri(accesoUrl + "/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .body(Identity.class);
                if (identity != null && identity.id() != null && identity.email() != null
                        && identity.role() != null && identity.active()) {
                    AuthenticatedIdentity principal = new AuthenticatedIdentity(
                            identity.id(), identity.email(), identity.name(), identity.role());
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null,
                            List.of(new SimpleGrantedAuthority(identity.role())));
                    request.setAttribute(TOKEN_ATTRIBUTE, token);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    expireCookie(response);
                }
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden exception) {
                expireCookie(response);
            } catch (ResourceAccessException exception) {
                LOGGER.error("El servicio de Acceso no está disponible.", exception);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "El servicio de Acceso no está disponible.");
                return;
            } catch (RestClientResponseException exception) {
                LOGGER.error("El servicio de Acceso respondió con HTTP {}.", exception.getStatusCode().value());
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY,
                        "No se pudo validar la sesión con el servicio de Acceso.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String getToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("JWT_TOKEN".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void expireCookie(HttpServletResponse response) {
        String secure = secureCookie ? "; Secure" : "";
        response.addHeader(HttpHeaders.SET_COOKIE,
                "JWT_TOKEN=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax" + secure);
    }

    private record Identity(String id, String email, String name, String role, boolean active) {
    }
}
