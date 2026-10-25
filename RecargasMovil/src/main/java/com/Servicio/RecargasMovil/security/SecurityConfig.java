package com.Servicio.RecargasMovil.security;

import jakarta.servlet.DispatcherType;
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

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
    CsrfTokenRepository csrfTokenRepository(
            @Value("${app.cookie.secure:false}") boolean secureCookie) {
        CookieCsrfTokenRepository repository = new CookieCsrfTokenRepository();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .secure(secureCookie)
                .sameSite("Lax"));
        return repository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${app.acceso.base-url}") String accesoUrl,
            @Value("${app.cookie.secure:false}") boolean secureCookie,
            RemoteAccessAuthenticationFilter authenticationFilter,
            CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers("/logout"))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/error", "/error/**").permitAll()
                        .requestMatchers("/admin/**").hasAnyAuthority("MODERATOR_RECARGAS_MOVIL", "ROLE_ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(
                        (request, response, authException) -> response.sendRedirect(accesoUrl + "/login"))
                        .accessDeniedPage("/error/403"))
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
