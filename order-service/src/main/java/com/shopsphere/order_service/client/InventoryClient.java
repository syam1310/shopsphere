package com.shopsphere.order_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

@Component
public class InventoryClient {

    private final RestClient restClient;
    private final String internalSecret;

    public InventoryClient(
            RestClient.Builder builder,
            @Value("${inventory-service.base-url}") String baseUrl,
            @Value("${inventory-service.internal-secret}") String internalSecret) {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        this.internalSecret = internalSecret;
    }

    public void reserveInventory(
            UUID reservationId,
            Long productId,
            Integer quantity) {

        ReserveInventoryRequest request =
                new ReserveInventoryRequest(reservationId, productId, quantity);

        restClient.post()
                .uri("/api/inventory/reserve")
                .header("X-Internal-Service-Secret", internalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public void releaseInventory(
            UUID reservationId,
            Long productId,
            Integer quantity) {

        ReleaseInventoryRequest request =
                new ReleaseInventoryRequest(reservationId, productId, quantity);

        restClient.post()
                .uri("/api/inventory/release")
                .header("X-Internal-Service-Secret", internalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private record ReserveInventoryRequest(
            UUID reservationId,
            Long productId,
            Integer quantity) {
    }

    private record ReleaseInventoryRequest(
            UUID reservationId,
            Long productId,
            Integer quantity) {
    }
}