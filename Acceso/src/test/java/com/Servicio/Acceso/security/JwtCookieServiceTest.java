package com.Servicio.Acceso.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtCookieServiceTest {

    private final JwtCookieService jwtCookieService = new JwtCookieService(86_400_000, false);

    @Test
    void writesJwtCookieUsingSerializedCookieValue() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtCookieService.addJwtCookie(response, "test-token");

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie)
                .contains("JWT_TOKEN=test-token")
                .contains("Path=/")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContain("DefaultResponseCookieBuilder");
    }

    @Test
    void clearsJwtCookieUsingSerializedCookieValue() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtCookieService.clearCookie(response);

        assertThat(response.getHeader("Set-Cookie"))
                .contains("JWT_TOKEN=")
                .contains("Max-Age=0")
                .doesNotContain("DefaultResponseCookieBuilder");
    }

    @Test
    void extractsJwtCookieWhenAnotherMalformedCookiePrecedesIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Cookie",
                "org.springframework.http.ResponseCookie$DefaultResponseCookieBuilder@3dee5232; "
                        + "JWT_TOKEN=test-token; JSESSIONID=session");

        assertThat(jwtCookieService.getJwtToken(request)).isEqualTo("test-token");
    }

}
