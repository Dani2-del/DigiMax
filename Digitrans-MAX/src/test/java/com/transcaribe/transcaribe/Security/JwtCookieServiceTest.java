package com.transcaribe.transcaribe.Security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class JwtCookieServiceTest {

    private final JwtCookieService jwtCookieService = new JwtCookieService();

    @Test
    void extractsJwtCookieWhenAnotherMalformedCookiePrecedesIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Cookie",
                "org.springframework.http.ResponseCookie$DefaultResponseCookieBuilder@3dee5232; "
                        + "JWT_TOKEN=test-token; JSESSIONID=session");

        assertThat(jwtCookieService.getJwtToken(request)).isEqualTo("test-token");
    }

}
