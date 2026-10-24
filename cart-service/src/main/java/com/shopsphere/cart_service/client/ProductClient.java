
package com.shopsphere.cart_service.client;

import com.shopsphere.cart_service.dto.ProductResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(
            RestClient.Builder restClientBuilder,
            @Value("${product-service.url}") String productServiceUrl) {

        this.restClient = restClientBuilder
                .baseUrl(productServiceUrl)
                .build();
    }

    public ProductResponse getProductById(
            Long productId, String authorization) {

        return restClient.get()
                .uri("/api/products/{id}", productId)
                .header("Authorization", authorization)
                .retrieve()
                .body(ProductResponse.class);
    }
}
