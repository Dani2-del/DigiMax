package com.Servicio.Acceso.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;

class AccesoErrorControllerTest {

    private final AccesoErrorController controller = new AccesoErrorController();

    @Test
    void rendersDedicatedPageAndPreservesStatusForCommonErrors() {
        assertErrorPage(403, "Acceso restringido");
        assertErrorPage(404, "Página no encontrada");
        assertErrorPage(500, "Ocurrió un problema");
    }

    @Test
    void errorForwardUsesContainerStatusCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        Object view = controller.handleError(request, response, model);

        assertThat(view).isEqualTo("error/general");
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(model.get("errorTitle")).isEqualTo("Página no encontrada");
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
