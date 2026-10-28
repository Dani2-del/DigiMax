package com.Servicio.Pagos.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class PagosControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsAndConsultsPendingPayment() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud("25000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idProveedor").isNotEmpty())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.urlPago").doesNotExist())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String idProveedor = response.get("idProveedor").asText();

        mockMvc.perform(get("/api/pagos/{idProveedor}", idProveedor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idProveedor").value(idProveedor))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void returnsRejectedStateForHighAmount() throws Exception {
        mockMvc.perform(post("/api/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud("1000000.01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"));
    }

    @Test
    void returnsConflictWhenReferenceIsReusedWithDifferentPaymentData() throws Exception {
        mockMvc.perform(post("/api/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud("referencia-compartida", "25000")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud("referencia-compartida", "30000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void returnsStructuredBadRequestForInvalidPayment() throws Exception {
        mockMvc.perform(post("/api/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "referencia": "",
                                  "monto": 0,
                                  "moneda": "cop",
                                  "descripcion": "",
                                  "pagador": "correo-invalido"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void returnsNotFoundForUnknownPaymentId() throws Exception {
        mockMvc.perform(get("/api/pagos/{idProveedor}", "no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        "No existe un pago con el ID de proveedor indicado."));
    }

    @Test
    void simulatesApprovalAndReturnsFinalStateOnConsultation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud("25000")))
                .andExpect(status().isCreated())
                .andReturn();
        String idProveedor = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("idProveedor").asText();

        mockMvc.perform(post("/api/pagos/{idProveedor}/aprobar", idProveedor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));
        mockMvc.perform(get("/api/pagos/{idProveedor}", idProveedor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));
    }

    @Test
    void preventsTransitionFromFinalStateAndReturnsConflict() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/pagos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud("25000")))
                .andExpect(status().isCreated())
                .andReturn();
        String idProveedor = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("idProveedor").asText();
        mockMvc.perform(post("/api/pagos/{idProveedor}/aprobar", idProveedor))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/pagos/{idProveedor}/rechazar", idProveedor))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "No se puede cambiar el pago de APROBADO a RECHAZADO."));
    }

    @Test
    void returnsNotFoundWhenSimulatingUnknownPayment() throws Exception {
        mockMvc.perform(post("/api/pagos/{idProveedor}/cancelar", "no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private String solicitud(String monto) {
        return solicitud(UUID.randomUUID().toString(), monto);
    }

    private String solicitud(String referencia, String monto) {
        return """
                {
                  "referencia": "%s",
                  "monto": %s,
                  "moneda": "COP",
                  "descripcion": "Recarga de prueba",
                  "pagador": "persona@example.com"
                }
                """.formatted(referencia, monto);
    }
}
