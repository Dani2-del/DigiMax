package com.Servicio.RecargasMovil.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;

class RecargasErrorControllerTest {

    private final RecargasErrorController controller = new RecargasErrorController("http://localhost:8080");

    @Test
    void rendersDedicatedPageAndPreservesStatusForCommonErrors() {
        assertErrorPage(403, "Acceso restringido");
        assertErrorPage(404, "Página no encontrada");
        assertErrorPage(500, "Ocurrió un problema");
    }

    @Test
    void apiErrorsReturnSafeJsonInsteadOfHtml() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/recharges");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object result = controller.handleStatus(500, request, response, new ExtendedModelMap());

        assertThat(result).isInstanceOf(org.springframework.http.ResponseEntity.class);
        assertThat(((org.springframework.http.ResponseEntity<?>) result).getStatusCode().value()).isEqualTo(500);
    }

    private void assertErrorPage(int status, String title) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        Object view = controller.handleStatus(status, request, response, model);

        assertThat(view).isEqualTo("error/general");
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(model.get("statusCode")).isEqualTo(status);
        assertThat(model.get("errorTitle")).isEqualTo(title);
    }
}
