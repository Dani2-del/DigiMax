package com.Servicio.Acceso.security;

import java.time.Duration;
import java.util.Enumeration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class JwtCookieService {

    public static final String JWT_COOKIE_NAME = "JWT_TOKEN";

    private final long expirationSeconds;
    private final boolean secure;

    public JwtCookieService(@Value("${app.jwt.expiration-ms}") long expirationMillis,
            @Value("${app.cookie.secure:false}") boolean secure) {
        this.expirationSeconds = expirationMillis / 1000;
        this.secure = secure;
    }

    public void addJwtCookie(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(token, Duration.ofSeconds(expirationSeconds)).build().toString());
    }

    public void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).build().toString());
    }

    public String getJwtToken(HttpServletRequest request) {
        Enumeration<String> cookieHeaders = request.getHeaders(HttpHeaders.COOKIE);
        while (cookieHeaders.hasMoreElements()) {
            for (String cookiePair : cookieHeaders.nextElement().split(";")) {
                String trimmedPair = cookiePair.trim();
                String prefix = JWT_COOKIE_NAME + "=";
                if (trimmedPair.startsWith(prefix) && trimmedPair.length() > prefix.length()) {
                    return trimmedPair.substring(prefix.length());
                }
            }
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (JWT_COOKIE_NAME.equals(cookie.getName())
                        && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String value, Duration maxAge) {
        return ResponseCookie.from(JWT_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge);
    }
}
