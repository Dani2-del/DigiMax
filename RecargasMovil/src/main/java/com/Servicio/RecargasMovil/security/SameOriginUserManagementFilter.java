package com.Servicio.RecargasMovil.security;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class SameOriginUserManagementFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isUserManagementRequest(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isSameOrigin(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"message\":\"La solicitud de usuarios debe originarse en Recargas.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    static boolean isUserManagementRequest(HttpServletRequest request) {
        String path = request.getServletPath();
        if (HttpMethod.POST.matches(request.getMethod())) {
            return "/admin/usuarios/buscar".equals(path);
        }
        if (HttpMethod.PATCH.matches(request.getMethod())
                && path.startsWith("/admin/usuarios/")) {
            String id = path.substring("/admin/usuarios/".length());
            return !id.isBlank() && !id.contains("/");
        }
        return false;
    }

    private static boolean isSameOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || origin.isBlank() || "null".equalsIgnoreCase(origin)) {
            return false;
        }
        try {
            URI originUri = new URI(origin);
            String originScheme = originUri.getScheme();
            String requestScheme = request.getScheme();
            if (originScheme == null || requestScheme == null
                    || originUri.getHost() == null
                    || originUri.getRawUserInfo() != null
                    || originUri.getRawQuery() != null
                    || originUri.getRawFragment() != null
                    || (originUri.getRawPath() != null && !originUri.getRawPath().isEmpty())
                    || !originScheme.equalsIgnoreCase(requestScheme)
                    || !originUri.getHost().equalsIgnoreCase(request.getServerName())) {
                return false;
            }
            return effectivePort(originScheme, originUri.getPort())
                    == effectivePort(requestScheme, request.getServerPort());
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private static int effectivePort(String scheme, int port) {
        if (port >= 0) {
            return port;
        }
        return "https".equals(scheme.toLowerCase(Locale.ROOT)) ? 443 : 80;
    }
}
