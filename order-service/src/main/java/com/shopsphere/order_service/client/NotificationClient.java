package com.shopsphere.order_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class NotificationClient {

    private final RestClient restClient;
    private final String internalSecret;

    public NotificationClient(
            RestClient.Builder builder,
            @Value("${notification-service.base-url}") String baseUrl,
            @Value("${notification-service.internal-secret}") String internalSecret) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.internalSecret = internalSecret;
    }

    public void enqueueOrderConfirmation(
            Long orderId,
            Long userId,
            String email,
            BigDecimal totalAmount) {

        OrderConfirmationRequest request = new OrderConfirmationRequest(
                orderId, userId, email, totalAmount);

        restClient.post()
                .uri("/api/notifications/orders/confirmed")
                .header("X-Internal-Secret", internalSecret)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new IllegalStateException(
                            "Notification Service rejected the order confirmation event: "
                                    + res.getStatusCode());
                })
                .toBodilessEntity();
    }

    public record OrderConfirmationRequest(
            Long orderId,
            Long userId,
            String email,
            BigDecimal totalAmount) {}
}
