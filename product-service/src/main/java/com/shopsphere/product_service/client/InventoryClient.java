package com.shopsphere.product_service.client;

import com.shopsphere.product_service.dto.InventoryAvailability;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(
            RestClient.Builder builder,
            @Value("${inventory-service.url}") String inventoryServiceUrl) {
        this.restClient = builder.baseUrl(inventoryServiceUrl).build();
    }

    public List<InventoryAvailability> getAvailability(List<Long> productIds) {
        if (productIds.isEmpty()) return List.of();
        List<InventoryAvailability> availability = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/inventory/availability")
                        .queryParam("productIds", productIds)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });

        if (availability == null) {
            throw new IllegalStateException("Inventory service returned no availability data");
        }
        return availability;
    }
}
