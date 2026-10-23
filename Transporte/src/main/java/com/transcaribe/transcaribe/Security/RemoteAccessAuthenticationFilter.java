package com.transcaribe.transcaribe.Security;

import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.transcaribe.transcaribe.service.AccessIdentityClient;
import com.transcaribe.transcaribe.service.AccessUser;

@Component
public class RemoteAccessAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteAccessAuthenticationFilter.class);

    private final JwtCookieService jwtCookieService;
    private final AccessIdentityClient accessIdentityClient;

    public RemoteAccessAuthenticationFilter(
            JwtCookieService jwtCookieService,
            AccessIdentityClient accessIdentityClient) {
        this.jwtCookieService = jwtCookieService;
        this.accessIdentityClient = accessIdentityClient;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/login")
                || path.equals("/registro")
                || path.startsWith("/verificar-otp")
                || path.startsWith("/olvido-password")
                || path.startsWith("/verificar-codigo-recuperar-password")
                || path.startsWith("/validar-codigo-restablecimiento")
                || path.startsWith("/restablecer-password")
                || path.startsWith("/confirmar-restablecimiento")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = getToken(request);
        SecurityContextHolder.clearContext();
        if (token != null) {
            try {
                AccessUser identity = accessIdentityClient.restClientGetMe(token);
                if (identity != null && identity.email() != null && identity.role() != null
                        && identity.id() != null && identity.active()) {
                    request.setAttribute(AccessIdentityClient.TOKEN_ATTRIBUTE, token);
                    request.setAttribute(AccessIdentityClient.USER_ATTRIBUTE, identity);
                    var principal = User.withUsername(identity.email())
                            .password("")
                            .authorities(identity.role())
                            .build();
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else if (identity != null) {
                    jwtCookieService.clearCookie(response);
                }
            } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized
                    | org.springframework.web.client.HttpClientErrorException.Forbidden exception) {
                LOGGER.warn("El servicio de Acceso rechazó el JWT remoto con HTTP {}.",
                        exception.getStatusCode().value());
                jwtCookieService.clearCookie(response);
            } catch (org.springframework.web.client.ResourceAccessException exception) {
                LOGGER.error("No se pudo validar la sesión con Acceso dentro del tiempo límite.", exception);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "El servicio de Acceso no respondió a tiempo.");
                return;
            } catch (org.springframework.web.client.RestClientResponseException exception) {
                LOGGER.error("El servicio de Acceso respondió con HTTP {}.", exception.getStatusCode().value());
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY,
                        "No se pudo validar la sesión con el servicio de Acceso.");
                return;
            } catch (IllegalStateException exception) {
                LOGGER.error("Acceso devolvió una identidad que Transporte no puede procesar.", exception);
                response.sendError(HttpServletResponse.SC_BAD_GATEWAY,
                        "No se pudo procesar la identidad del usuario.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String getToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")
                && authorization.length() > "Bearer ".length()) {
            return authorization.substring(7);
        }
        return jwtCookieService.getJwtToken(request);
    }
}
