package com.Servicio.Billetera.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

    @Bean
    RemoteAccessAuthenticationFilter remoteAccessAuthenticationFilter(
            @Value("${app.acceso.base-url}") String accesoUrl,
            @Value("${app.cookie.secure:false}") boolean secureCookie) {
        return new RemoteAccessAuthenticationFilter(accesoUrl, secureCookie);
    }

    @Bean
    FilterRegistrationBean<RemoteAccessAuthenticationFilter> filterRegistration(
            RemoteAccessAuthenticationFilter filter) {
        FilterRegistrationBean<RemoteAccessAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${app.acceso.base-url}") String accesoUrl,
            @Value("${app.cookie.secure:false}") boolean secureCookie,
            @Value("${app.lector.api-key:}") String lectorApiKey,
            RemoteAccessAuthenticationFilter authenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/internal/**", "/logout"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/error", "/error/**").permitAll()
                        .requestMatchers("/internal/**").hasAuthority("SERVICE_LECTOR")
                        .requestMatchers("/admin/tarjetas-desactivadas/**")
                        .hasAnyAuthority("MODERATOR_BILLETERA", "ROLE_ADMIN")
                        .requestMatchers("/admin/**").hasAnyAuthority("MODERATOR_BILLETERA", "ROLE_ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new ServiceKeyAuthenticationFilter(lectorApiKey),
                        RemoteAccessAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(
                        (request, response, authException) -> {
                            if (request.getServletPath().startsWith("/internal/")) {
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                            } else {
                                response.sendRedirect(accesoUrl + "/login");
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (request.getServletPath().startsWith("/internal/")) {
                                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                            } else {
                                response.sendRedirect("/error/403");
                            }
                        }))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .addLogoutHandler((request, response, authentication) -> {
                            ResponseCookie cookie = ResponseCookie.from("JWT_TOKEN", "")
                                    .path("/")
                                    .httpOnly(true)
                                    .secure(secureCookie)
                                    .sameSite("Lax")
                                    .maxAge(0)
                                    .build();
                            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
                        })
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.sendRedirect(accesoUrl + "/")));
        return http.build();
    }
}
