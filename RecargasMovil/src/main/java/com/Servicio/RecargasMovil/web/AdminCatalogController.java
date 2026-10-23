package com.Servicio.RecargasMovil.web;

import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.Servicio.RecargasMovil.model.OperadorMovil;
import com.Servicio.RecargasMovil.model.PaqueteMovil;
import com.Servicio.RecargasMovil.repository.OperadorMovilRepository;
import com.Servicio.RecargasMovil.repository.PaqueteMovilRepository;
import com.Servicio.RecargasMovil.service.RecargasService;

@Controller
@RequestMapping("/admin/catalogo")
public class AdminCatalogController {

    private final RecargasService recargasService;
    private final OperadorMovilRepository operadores;
    private final PaqueteMovilRepository paquetes;

    public AdminCatalogController(
            RecargasService recargasService,
            OperadorMovilRepository operadores,
            PaqueteMovilRepository paquetes) {
        this.recargasService = recargasService;
        this.operadores = operadores;
        this.paquetes = paquetes;
    }

    @GetMapping
    public String index(Model model) {
        var allOperators = operadores.findAll();
        var allPackages = paquetes.findAllByOrderByNombreAsc();
        Map<String, String> operatorNames = allOperators.stream()
                .collect(Collectors.toMap(OperadorMovil::getId, OperadorMovil::getNombre));
        model.addAttribute("operadores", allOperators);
        model.addAttribute("operadoresActivos", recargasService.operadoresActivos());
        model.addAttribute("paquetes", allPackages);
        model.addAttribute("operatorNames", operatorNames);
        return "admin-catalogo";
    }

    @PostMapping("/operadores")
    public String createOperator(
            @RequestParam String nombre,
            RedirectAttributes redirectAttributes) {
        try {
            recargasService.crearOperador(nombre);
            redirectAttributes.addFlashAttribute("mensaje", "Operador creado.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/catalogo";
    }

    @PostMapping("/paquetes")
    public String createPackage(
            @RequestParam String operadorId,
            @RequestParam String nombre,
            @RequestParam(required = false) String descripcion,
            @RequestParam BigDecimal precio,
            RedirectAttributes redirectAttributes) {
        try {
            recargasService.crearPaquete(operadorId, nombre, descripcion, precio);
            redirectAttributes.addFlashAttribute("mensaje", "Paquete creado.");
        } catch (IllegalArgumentException | ArithmeticException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/catalogo";
    }

    @PostMapping("/operadores/estado")
    public String toggleOperator(
            @RequestParam String operadorId,
            RedirectAttributes redirectAttributes) {
        OperadorMovil operator = operadores.findById(operadorId).orElse(null);
        if (operator == null) {
            redirectAttributes.addFlashAttribute("error", "No se encontró el operador seleccionado.");
        } else {
            operator.setActivo(!operator.isActivo());
            operadores.save(operator);
            redirectAttributes.addFlashAttribute("mensaje",
                    operator.isActivo() ? "Operador activado." : "Operador desactivado.");
        }
        return "redirect:/admin/catalogo";
    }

    @PostMapping("/paquetes/estado")
    public String togglePackage(
            @RequestParam String paqueteId,
            RedirectAttributes redirectAttributes) {
        PaqueteMovil mobilePackage = paquetes.findById(paqueteId).orElse(null);
        if (mobilePackage == null) {
            redirectAttributes.addFlashAttribute("error", "No se encontró el paquete seleccionado.");
        } else {
            mobilePackage.setActivo(!mobilePackage.isActivo());
            paquetes.save(mobilePackage);
            redirectAttributes.addFlashAttribute("mensaje",
                    mobilePackage.isActivo() ? "Paquete activado." : "Paquete desactivado.");
        }
        return "redirect:/admin/catalogo";
    }
}
