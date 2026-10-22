package com.transcaribe.transcaribe.Security;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.transcaribe.transcaribe.Model.Usuario;
import com.transcaribe.transcaribe.Repository.UsuarioRepository;

@Component
public class RemoteAccessAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteAccessAuthenticationFilter.class);

    private static final Duration ACCESS_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration ACCESS_READ_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final String accesoUrl;
    private final JwtCookieService jwtCookieService;
    private final UsuarioRepository usuarioRepository;

    public RemoteAccessAuthenticationFilter(
            @Value("${app.acceso.base-url:http://localhost:8080}") String accesoUrl,
            JwtCookieService jwtCookieService,
            UsuarioRepository usuarioRepository) {
        this.accesoUrl = accesoUrl;
        this.jwtCookieService = jwtCookieService;
        this.usuarioRepository = usuarioRepository;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(ACCESS_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(ACCESS_READ_TIMEOUT);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
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
                Identity identity = restClient.get()
                        .uri(accesoUrl + "/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .retrieve()
                        .body(Identity.class);
                if (identity != null && identity.email() != null && identity.role() != null) {
                    synchronizeTransportAccount(identity);
                    var principal = User.withUsername(identity.email())
                            .password("")
                            .authorities(identity.role())
                            .build();
                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden exception) {
                LOGGER.warn("El servicio de Acceso rechazó el JWT remoto con HTTP {}.",
                        exception.getStatusCode().value());
                jwtCookieService.clearCookie(response);
            } catch (ResourceAccessException exception) {
                LOGGER.error("No se pudo validar la sesión con Acceso dentro del tiempo límite.", exception);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "El servicio de Acceso no respondió a tiempo.");
                return;
            } catch (DataAccessException exception) {
                LOGGER.error("No se pudo sincronizar la cuenta autenticada con la base de datos de transporte.",
                        exception);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "No se pudo cargar la cuenta en el servicio de transporte.");
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

    private void synchronizeTransportAccount(Identity identity) {
        if (identity.id() == null || identity.name() == null) {
            throw new IllegalStateException("Acceso devolvió una identidad incompleta.");
        }

        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(identity.email())
                .orElseGet(Usuario::new);
        boolean changed = usuario.getId() == null;
        if (changed) {
            usuario.setId(identity.id());
            usuario.setCorreo(identity.email());
            usuario.setVerificado(true);
            usuario.setActivo(true);
        }
        if (!Objects.equals(usuario.getNombre(), identity.name())) {
            usuario.setNombre(identity.name());
            changed = true;
        }
        if (!Objects.equals(usuario.getRole(), identity.role())) {
            usuario.setRole(identity.role());
            changed = true;
        }
        if (!usuario.isVerificado()) {
            usuario.setVerificado(true);
            changed = true;
        }
        if (!usuario.isActivo()) {
            usuario.setActivo(true);
            changed = true;
        }
        if (changed) {
            usuarioRepository.save(usuario);
        }
    }

    private String getToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")
                && authorization.length() > "Bearer ".length()) {
            return authorization.substring(7);
        }
        return jwtCookieService.getJwtToken(request);
    }

    private record Identity(String id, String email, String name, String role) {
    }
}
