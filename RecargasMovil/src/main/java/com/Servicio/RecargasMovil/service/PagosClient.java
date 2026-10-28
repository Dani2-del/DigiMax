package com.Servicio.RecargasMovil.service;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class PagosClient {

    private final RestClient restClient;
    private final String pagosUrl;

    public PagosClient(@Value("${app.pagos.base-url}") String pagosUrl) {
        this.pagosUrl = pagosUrl.replaceAll("/+$", "");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public ResultadoPago iniciar(
            String referencia,
            BigDecimal monto,
            String descripcion,
            String pagador) {
        ResultadoPago resultado = restClient.post()
                .uri(pagosUrl + "/api/pagos")
                .body(new SolicitudPago(referencia, monto, "COP", descripcion, pagador))
                .retrieve()
                .body(ResultadoPago.class);
        return validar(resultado);
    }

    public ResultadoPago consultar(String idProveedor) {
        ResultadoPago resultado = restClient.get()
                .uri(pagosUrl + "/api/pagos/{idProveedor}", idProveedor)
                .retrieve()
                .body(ResultadoPago.class);
        ResultadoPago validado = validar(resultado);
        if (!idProveedor.equals(validado.idProveedor())) {
            throw new IllegalStateException("Pagos devolvió un resultado para otro identificador.");
        }
        return validado;
    }

    private static ResultadoPago validar(ResultadoPago resultado) {
        if (resultado == null || !StringUtils.hasText(resultado.idProveedor())
                || !StringUtils.hasText(resultado.estado())) {
            throw new IllegalStateException("Pagos devolvió una respuesta incompleta.");
        }
        return resultado;
    }

    private record SolicitudPago(
            String referencia,
            BigDecimal monto,
            String moneda,
            String descripcion,
            String pagador) {
    }

    public record ResultadoPago(String idProveedor, String estado, String urlPago) {
    }
}
