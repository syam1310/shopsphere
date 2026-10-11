package com.shopsphere.order_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.shopsphere.order_service.exception.ProductNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

@Component
public class ProductClient {

    private final RestClient restClient;

    public ProductClient(
            @Value("${product-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

//    public ProductClient() {
//        this.restClient = RestClient.builder()
//                .baseUrl("http://localhost:8082")
//                .build();
//    }


    public ProductResponse getProductById(Long productId, String token) {
        try {
            return restClient.get()
                    .uri("/api/products/{id}", productId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(ProductResponse.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ProductNotFoundException(productId);
        } catch (RestClientException ex) {
            throw new ProductServiceException(
                    "Unable to retrieve product with ID: " + productId, ex);
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductResponse(
            Long id,
            String name,
            BigDecimal price,
            Integer quantity,
            boolean active
    ) {
    }

    public static class ProductServiceException extends RuntimeException {
        public ProductServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
