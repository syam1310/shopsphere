
package com.shopsphere.order_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class CartClient {

    private final RestClient restClient;

    public CartClient(
            @Value("${cart-service.base-url}") String baseUrl) {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public CartResponse getCart(String authorizationHeader) {
        try {
            return restClient.get()
                    .uri("/api/cart")
                    .header("Authorization", authorizationHeader)
                    .retrieve()
                    .body(CartResponse.class);

        } catch (RestClientException ex) {
            throw new CartServiceException(
                    "Unable to retrieve cart from Cart Service", ex);
        }
    }

    public void clearCart(String authorizationHeader) {
        try {
            restClient.delete()
                    .uri("/api/cart")
                    .header("Authorization", authorizationHeader)
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException ex) {
            throw new CartServiceException(
                    "Order may have been created, but clearing the cart failed. "
                            + "Check the order before retrying checkout.", ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CartResponse(
            Long cartId,
            Long userId,
            List<CartItemResponse> items,
            BigDecimal total,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CartItemResponse(
            Long productId,
            String productName,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal subtotal
    ) {
    }

    public static class CartServiceException extends RuntimeException {
        public CartServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
