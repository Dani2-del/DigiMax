package com.transcaribe.transcaribe.Controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.transcaribe.transcaribe.Model.SistemaTransporte;

class TransportErrorControllerTest {

    private final TransportErrorController controller = new TransportErrorController();

    @Test
    void errorForwardUsesStatusAndSelectedTransportSystem() {
        for (SistemaTransporte system : SistemaTransporte.disponibles()) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
            request.setSession(new MockHttpSession());
            request.getSession().setAttribute(SistemaTransporte.SESSION_ATTRIBUTE, system);
            MockHttpServletResponse response = new MockHttpServletResponse();
            ExtendedModelMap model = new ExtendedModelMap();

            String view = controller.handleError(request, response, model);

            assertThat(view).isEqualTo("error/404");
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(model.get("sistemaActual")).isEqualTo(system.name());
        }
    }

    @Test
    void directErrorRouteSetsItsStatusAndUsesDefaultSystemWithoutSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.handleStatus(403, request, response, model);

        assertThat(view).isEqualTo("error/403");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(model.get("sistemaActual")).isEqualTo(SistemaTransporte.TRANSCARIBE.name());
    }

    @Test
    void unknownErrorStatusUsesGenericServerErrorPage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.handleStatus(400, request, response, model);

        assertThat(view).isEqualTo("error/500");
        assertThat(response.getStatus()).isEqualTo(500);
    }

    @Test
    void errorDispatchFromPostRequestUsesTheCustomErrorPage() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500))
                .andExpect(status().isInternalServerError())
                .andExpect(view().name("error/500"));
    }
}
