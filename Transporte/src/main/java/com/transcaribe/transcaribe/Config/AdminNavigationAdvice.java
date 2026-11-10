package com.transcaribe.transcaribe.Config;

import com.transcaribe.transcaribe.Model.Usuario;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AdminNavigationAdvice {

    @ModelAttribute("puedeVerPanelAdmin")
    public boolean puedeVerPanelAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> Usuario.ROLE_ADMIN.equals(authority.getAuthority())
                                || Usuario.ROLE_MODERATOR.equals(authority.getAuthority()));
    }

    @ModelAttribute("esAdministradorGlobal")
    public boolean esAdministradorGlobal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> Usuario.ROLE_ADMIN.equals(authority.getAuthority()));
    }
}
