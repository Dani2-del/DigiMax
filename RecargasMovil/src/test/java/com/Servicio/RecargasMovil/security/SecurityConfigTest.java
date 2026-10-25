package com.Servicio.RecargasMovil.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;

class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void csrfTokenUsesCookieWithoutCreatingAnHttpSession() {
        CookieCsrfTokenRepository repository =
                (CookieCsrfTokenRepository) securityConfig.csrfTokenRepository(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);

        repository.saveToken(token, request, response);

        assertThat(request.getSession(false)).isNull();
        Cookie savedCookie = response.getCookie("XSRF-TOKEN");
        assertThat(savedCookie).isNotNull();
        assertThat(savedCookie.getValue()).isEqualTo(token.getToken());
        assertThat(savedCookie.getPath()).isEqualTo("/");
        assertThat(savedCookie.isHttpOnly()).isTrue();
        assertThat(savedCookie.getSecure()).isFalse();
        assertThat(savedCookie.getAttribute("SameSite")).isEqualTo("Lax");

        request.setCookies(new Cookie("XSRF-TOKEN", token.getToken()));
        assertThat(repository.loadToken(request)).isNotNull()
                .extracting(CsrfToken::getToken)
                .isEqualTo(token.getToken());
    }

    @Test
    void csrfCookieIsMarkedSecureWhenSecureCookiesAreEnabled() {
        CookieCsrfTokenRepository repository =
                (CookieCsrfTokenRepository) securityConfig.csrfTokenRepository(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(repository.generateToken(request), request, response);

        assertThat(response.getCookie("XSRF-TOKEN").getSecure()).isTrue();
    }
}
