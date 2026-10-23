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
            RemoteAccessAuthenticationFilter authenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/internal/**", "/logout"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/error/**").permitAll()
                        .requestMatchers("/internal/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception.authenticationEntryPoint(
                        (request, response, authException) -> response.sendRedirect(accesoUrl + "/login")))
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
