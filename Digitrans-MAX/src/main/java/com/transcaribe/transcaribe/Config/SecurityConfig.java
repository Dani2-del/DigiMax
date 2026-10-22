package com.transcaribe.transcaribe.Config;

import com.transcaribe.transcaribe.Security.JwtCookieService;
import com.transcaribe.transcaribe.Security.RemoteAccessAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RemoteAccessAuthenticationFilter remoteAccessAuthenticationFilter;
    private final JwtCookieService jwtCookieService;
    private final String accesoUrl;

    public SecurityConfig(RemoteAccessAuthenticationFilter remoteAccessAuthenticationFilter,
                          JwtCookieService jwtCookieService,
                          @Value("${app.acceso.base-url:http://localhost:8080}") String accesoUrl) {
        this.remoteAccessAuthenticationFilter = remoteAccessAuthenticationFilter;
        this.jwtCookieService = jwtCookieService;
        this.accesoUrl = accesoUrl;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/mapa-rutas",
                    "/mapa-rutas/api/**",
                    "/Rutas/**",
                    "/login",
                    "/registro",
                    "/verificar-otp/**",
                    "/olvido-password",
                    "/verificar-codigo-recuperar-password/**",
                    "/validar-codigo-restablecimiento",
                    "/restablecer-password",
                    "/confirmar-restablecimiento",
                    "/css/**",
                    "/error/**",
                    "/images/**",
                    "/js/**"
                ).permitAll()
                .requestMatchers("/choose-view").authenticated()
                .requestMatchers("/admin/seleccionar-entidad", "/admin/contexto").hasRole("ADMIN")
                .requestMatchers("/chat/**").hasAnyRole("ADMIN", "MODERATOR", "CONDUCTOR")
                .requestMatchers("/admin/**").hasAnyRole("ADMIN", "MODERATOR")
                .requestMatchers("/conductor/**").hasRole("CONDUCTOR")
                .anyRequest().authenticated()
            )
            .addFilterBefore(remoteAccessAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl(accesoUrl + "/")
                .deleteCookies("JWT_TOKEN")
                .addLogoutHandler((request, response, authentication) ->
                    jwtCookieService.clearCookie(response))
                .invalidateHttpSession(true)
                .permitAll()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .maximumSessions(1)
                .expiredUrl("/login?expired=true")
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendRedirect(accesoUrl + "/login"))
                .accessDeniedPage("/error/403")
            );
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<RemoteAccessAuthenticationFilter> remoteAccessFilterRegistration(
            RemoteAccessAuthenticationFilter filter) {
        FilterRegistrationBean<RemoteAccessAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
