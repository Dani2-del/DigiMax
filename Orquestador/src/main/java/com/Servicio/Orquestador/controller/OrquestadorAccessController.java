package com.Servicio.Orquestador.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.Servicio.Orquestador.security.OrquestadorAccessFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
public class OrquestadorAccessController {

    private static final byte[] USERNAME = "DigitransPA".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PASSWORD = "ProyectoAula2026".getBytes(StandardCharsets.UTF_8);

    @GetMapping("/acceso")
    public String login() {
        return "redirect:/login.html";
    }

    @PostMapping("/acceso")
    public String authenticate(@RequestParam(name = "usuario", defaultValue = "") String suppliedUser,
            @RequestParam(name = "clave", defaultValue = "") String suppliedPassword,
            HttpServletRequest request) {
        boolean validUser = MessageDigest.isEqual(USERNAME, suppliedUser.getBytes(StandardCharsets.UTF_8));
        boolean validPassword = MessageDigest.isEqual(PASSWORD, suppliedPassword.getBytes(StandardCharsets.UTF_8));
        if (!validUser || !validPassword) {
            return "redirect:/login.html?error=1";
        }

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(OrquestadorAccessFilter.SESSION_ATTRIBUTE, Boolean.TRUE);
        return "redirect:/";
    }

    @PostMapping("/salir")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login.html?salio=1";
    }
}
