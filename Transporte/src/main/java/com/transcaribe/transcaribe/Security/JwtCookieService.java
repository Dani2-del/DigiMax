package com.transcaribe.transcaribe.Security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.util.Enumeration;

@Service
public class JwtCookieService {

    public static final String JWT_COOKIE_NAME = "JWT_TOKEN";
    private final int cookieExpiry = 86400; 

    public void addJwtCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(JWT_COOKIE_NAME, token);
        cookie.setHttpOnly(true); 
        cookie.setSecure(false);  
        cookie.setPath("/");
        cookie.setMaxAge(cookieExpiry);
        response.addCookie(cookie);
    }

    public void clearCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(JWT_COOKIE_NAME, null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    public String getJwtToken(HttpServletRequest request) {
        Enumeration<String> cookieHeaders = request.getHeaders("Cookie");
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
}