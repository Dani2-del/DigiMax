package com.Servicio.Acceso.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.Servicio.Acceso.model.Usuario;
import com.Servicio.Acceso.security.JwtCookieService;
import com.Servicio.Acceso.security.JwtService;
import com.Servicio.Acceso.service.AccountService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtCookieService jwtCookieService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        controller = new AuthController(
                accountService, userDetailsService, jwtService, jwtCookieService, false);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verificationAuthenticatesAccountAndRedirectsToConfirmation() {
        UserDetails user = new User(
                "persona@example.com", "encoded", List.of(new SimpleGrantedAuthority(Usuario.ROLE_USER)));
        when(accountService.verifyAccount("persona@example.com", "123456"))
                .thenReturn(AccountService.VerificationResult.VERIFIED);
        when(userDetailsService.loadUserByUsername("persona@example.com")).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("signed-jwt");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String result = controller.verificarOtp(
                "persona@example.com", "123456", new ExtendedModelMap(),
                new RedirectAttributesModelMap(), request, response);

        assertThat(result).isEqualTo("redirect:/registro/verificado");
        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession().getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("persona@example.com");
        verify(jwtCookieService).addJwtCookie(response, "signed-jwt");
        verify(userDetailsService).loadUserByUsername("persona@example.com");
    }

    @Test
    void verifiedAccountPageRequiresAuthentication() {
        assertThat(controller.registroVerificado(null)).isEqualTo("redirect:/login");
    }
}
