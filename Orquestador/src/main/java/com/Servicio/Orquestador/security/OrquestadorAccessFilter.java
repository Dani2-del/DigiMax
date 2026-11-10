package com.Servicio.Orquestador.security;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Requires a password-authenticated session for the Orchestrator UI and its UI API. */
@Component
public class OrquestadorAccessFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTRIBUTE = OrquestadorAccessFilter.class.getName() + ".authenticated";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/login.html") || path.equals("/acceso") || path.equals("/salir")
                || path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/") || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Object authenticated = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute(SESSION_ATTRIBUTE);
        if (Boolean.TRUE.equals(authenticated)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (request.getServletPath().startsWith("/api/")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            response.sendRedirect("/login.html");
        }
    }
}
