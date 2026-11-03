package com.Servicio.RecargasMovil.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
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
        Cookie savedCookie = response.getCookie("RECARGAS_XSRF-TOKEN");
        assertThat(savedCookie).isNotNull();
        assertThat(savedCookie.getValue()).isEqualTo(token.getToken());
        assertThat(savedCookie.getPath()).isEqualTo("/");
        assertThat(savedCookie.isHttpOnly()).isTrue();
        assertThat(savedCookie.getSecure()).isFalse();
        assertThat(savedCookie.getAttribute("SameSite")).isEqualTo("Lax");

        request.setCookies(new Cookie("RECARGAS_XSRF-TOKEN", token.getToken()));
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

        assertThat(response.getCookie("RECARGAS_XSRF-TOKEN").getSecure()).isTrue();
    }

    @Test
    void userManagementEndpointAllowsMatchingSameOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/admin/usuarios/user-1");
        request.setServletPath("/admin/usuarios/user-1");
        request.setServerName("localhost");
        request.setServerPort(8083);
        request.addHeader("Origin", "http://localhost:8083");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new SameOriginUserManagementFilter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void userManagementEndpointRejectsCrossOriginAndMissingOrigin() throws Exception {
        for (String origin : new String[] { "https://evil.example", null }) {
            MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/admin/usuarios/user-1");
            request.setServletPath("/admin/usuarios/user-1");
            request.setServerName("localhost");
            request.setServerPort(8083);
            if (origin != null) {
                request.addHeader("Origin", origin);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();

            new SameOriginUserManagementFilter().doFilter(
                    request, response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("debe originarse en Recargas");
        }
    }

    @Test
    void onlyUserSearchAndRoleUpdateEndpointsUseOriginBasedCsrfProtection() {
        MockHttpServletRequest search = new MockHttpServletRequest("POST", "/admin/usuarios/buscar");
        search.setServletPath("/admin/usuarios/buscar");
        MockHttpServletRequest update = new MockHttpServletRequest("PATCH", "/admin/usuarios/user-1");
        update.setServletPath("/admin/usuarios/user-1");
        MockHttpServletRequest unrelated = new MockHttpServletRequest("POST", "/admin/paquetes");
        unrelated.setServletPath("/admin/paquetes");

        assertThat(SameOriginUserManagementFilter.isUserManagementRequest(search)).isTrue();
        assertThat(SameOriginUserManagementFilter.isUserManagementRequest(update)).isTrue();
        assertThat(SameOriginUserManagementFilter.isUserManagementRequest(unrelated)).isFalse();
    }
}
