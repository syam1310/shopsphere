package com.shopsphere.order_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Component
public class PaymentClient {

    private final RestClient restClient;
    private final String internalSecret;

    public PaymentClient(
            @Value("${payment-service.base-url}") String baseUrl,
            @Value("${payment-service.internal-secret}") String internalSecret) {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        this.internalSecret = internalSecret;
    }


    public PaymentResponse createPayment(
            CreatePaymentRequest request,
            String idempotencyKey) {

        try {
            return restClient.post()
                    .uri("/api/payments")
                    .header("Idempotency-Key", idempotencyKey)
                    .body(request)
                    .retrieve()
                    .body(PaymentResponse.class);

        } catch (RestClientException ex) {
            throw new PaymentServiceException(
                    "Unable to process payment through Payment Service", ex);
        }
    }

    public PaymentResponse getPaymentByIdempotencyKey(
            String idempotencyKey,
            Long userId) {

        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/payments/internal/by-idempotency-key")
                            .queryParam("key", idempotencyKey)
                            .queryParam("userId", userId)
                            .build())
                    .header("X-Internal-Service-Secret", internalSecret)
                    .retrieve()
                    .body(PaymentResponse.class);

        } catch (RestClientException ex) {
            throw new PaymentServiceException(
                    "Unable to retrieve payment status from Payment Service", ex);
        }
    }

    public record CreatePaymentRequest(
            Long orderId,
            Long userId,
            BigDecimal amount,
            String paymentMethod) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentResponse(
            Long id,
            Long orderId,
            Long userId,
            BigDecimal amount,
            String paymentMethod,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public static class PaymentServiceException extends RuntimeException {
        public PaymentServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
