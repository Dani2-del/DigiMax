package com.Servicio.Lector.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.Servicio.Lector.service.RfidService;

@Controller
public class LectorPageController {

    private final RfidService rfidService;

    public LectorPageController(RfidService rfidService) {
        this.rfidService = rfidService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("tarjetas", rfidService.listarTarjetas());
        return "lector";
    }
}
