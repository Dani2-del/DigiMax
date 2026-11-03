package com.transcaribe.transcaribe.Config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.transcaribe.transcaribe.Model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminNavigationAdviceTest {

    private final AdminNavigationAdvice advice = new AdminNavigationAdvice();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesGlobalAdministratorFlagOnlyForGlobalAdministrators() {
        authenticate(Usuario.ROLE_ADMIN);
        assertThat(advice.esAdministradorGlobal()).isTrue();

        authenticate(Usuario.ROLE_MODERATOR);
        assertThat(advice.esAdministradorGlobal()).isFalse();
    }

    private static void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin@example.com", null, List.of(new SimpleGrantedAuthority(authority))));
    }
}
