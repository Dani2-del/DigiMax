package com.Servicio.Acceso.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.Servicio.Acceso.service.CustomUserDetailsService;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final LoginSuccessHandler loginSuccessHandler;
    private final OAuth2AuthenticationSuccessHandler oauthSuccessHandler;
    private final JwtCookieService jwtCookieService;
    private final boolean googleOAuthEnabled;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            LoginSuccessHandler loginSuccessHandler,
            OAuth2AuthenticationSuccessHandler oauthSuccessHandler,
            JwtCookieService jwtCookieService,
            @Value("${app.oauth.google.enabled:false}") boolean googleOAuthEnabled) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.loginSuccessHandler = loginSuccessHandler;
        this.oauthSuccessHandler = oauthSuccessHandler;
        this.jwtCookieService = jwtCookieService;
        this.googleOAuthEnabled = googleOAuthEnabled;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    DaoAuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            DaoAuthenticationProvider authenticationProvider) throws Exception {
        http
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/login", "/registro", "/verificar-otp/**",
                                "/olvido-password", "/verificar-codigo-recuperar-password",
                                "/validar-codigo-restablecimiento", "/restablecer-password",
                                "/confirmar-restablecimiento", "/css/**", "/js/**", "/images/**",
                                "/oauth2/**", "/login/oauth2/**", "/error")
                        .permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("correo")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler)
                        .failureHandler((request, response, exception) -> {
                            if (exception instanceof LockedException) {
                                String correo = java.net.URLEncoder.encode(
                                        request.getParameter("correo"), java.nio.charset.StandardCharsets.UTF_8);
                                response.sendRedirect("/verificar-otp?correo=" + correo);
                            } else if (exception instanceof DisabledException) {
                                response.sendRedirect("/login?disabled=true");
                            } else {
                                response.sendRedirect("/login?error=true");
                            }
                        })
                        .permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .deleteCookies("JSESSIONID")
                        .addLogoutHandler((request, response, authentication) ->
                                jwtCookieService.clearCookie(response))
                        .invalidateHttpSession(true)
                        .permitAll());
        http.exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, authException) -> {
            if (request.getRequestURI().startsWith("/api/")) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            } else {
                response.sendRedirect("/login");
            }
        }));
        if (googleOAuthEnabled) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .successHandler(oauthSuccessHandler)
                    .failureHandler((request, response, exception) -> {
                        if (exception instanceof OAuth2AuthenticationException oauthException) {
                            LOGGER.warn("Google OAuth login failed: {} ({})",
                                    exception.getClass().getSimpleName(),
                                    oauthException.getError().getErrorCode());
                        } else {
                            LOGGER.warn("Google OAuth login failed: {}",
                                    exception.getClass().getSimpleName());
                        }
                        response.sendRedirect("/login?error=true");
                    }));
        }
        return http.build();
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
