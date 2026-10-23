package com.Servicio.Acceso.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class UniversalWalletProvisioningClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(UniversalWalletProvisioningClient.class);

    private final RestClient restClient;
    private final String billeteraUrl;
    private final String provisioningKey;

    public UniversalWalletProvisioningClient(
            @Value("${app.services.billetera-url}") String billeteraUrl,
            @Value("${app.services.billetera-provisioning-key:}") String provisioningKey) {
        this.billeteraUrl = billeteraUrl;
        this.provisioningKey = provisioningKey;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean provision(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Se requiere una identidad válida para emitir la tarjeta universal.");
        }
        if (provisioningKey.isBlank()) {
            LOGGER.error("No está configurada la credencial para aprovisionar tarjetas universales.");
            return false;
        }
        try {
            restClient.post()
                    .uri(billeteraUrl + "/internal/wallets")
                    .header("X-Billetera-Provisioning-Key", provisioningKey)
                    .body(new ProvisionRequest(userId))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (ResourceAccessException exception) {
            LOGGER.error("No se pudo contactar Billetera para emitir la tarjeta del usuario {}.", userId, exception);
            return false;
        } catch (RestClientResponseException exception) {
            LOGGER.error("Billetera rechazó el aprovisionamiento del usuario {} con HTTP {}.",
                    userId, exception.getStatusCode().value());
            return false;
        }
    }

    private record ProvisionRequest(String userId) {
    }
}
