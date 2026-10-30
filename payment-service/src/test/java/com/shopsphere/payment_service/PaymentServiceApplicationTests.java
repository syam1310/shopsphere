package com.shopsphere.payment_service;

import com.shopsphere.payment_service.dto.PaymentResponse;
import com.shopsphere.payment_service.entity.PaymentMethod;
import com.shopsphere.payment_service.entity.PaymentStatus;
import com.shopsphere.payment_service.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "internal-service.secret=test-internal-secret")
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class PaymentServiceApplicationTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String INTERNAL_SECRET = "test-internal-secret";
    private static final String LOOKUP_URL =
            "/api/payments/internal/by-idempotency-key";
    private static final String PAYMENT_URL = "/api/payments";
    private static final String SECRET_HEADER = "X-Internal-Service-Secret";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void contextLoads() {
    }

    @Test
    void internalLookup_withValidSecret_returnsPayment() throws Exception {
        PaymentResponse payment = new PaymentResponse(
                5L,
                22L,
                10L,
                new BigDecimal("500.00"),
                PaymentMethod.UPI,
                PaymentStatus.SUCCESS,
                Instant.parse("2026-10-24T04:00:00Z"),
                Instant.parse("2026-10-24T04:00:00Z")
        );

        when(paymentService.getPaymentByIdempotencyKey(
                "checkout-test-123", 10L))
                .thenReturn(payment);

        mockMvc.perform(get(LOOKUP_URL)
                        .param("key", "checkout-test-123")
                        .param("userId", "10")
                        .header(SECRET_HEADER, INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.orderId").value(22))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(paymentService).getPaymentByIdempotencyKey(
                "checkout-test-123", 10L);
    }

    @Test
    void internalLookup_withoutSecret_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(LOOKUP_URL)
                        .param("key", "checkout-test-123")
                        .param("userId", "10"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService);
    }

    @Test
    void internalLookup_withInvalidSecret_returnsUnauthorized()
            throws Exception {
        mockMvc.perform(get(LOOKUP_URL)
                        .param("key", "checkout-test-123")
                        .param("userId", "10")
                        .header(SECRET_HEADER, "wrong-secret"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService);
    }

    @Test
    void internalLookup_whenPaymentDoesNotExist_returnsNotFound()
            throws Exception {
        when(paymentService.getPaymentByIdempotencyKey(
                "missing-checkout-key", 10L))
                .thenThrow(new PaymentService.PaymentNotFoundException(
                        "No payment found for the supplied checkout key"));

        mockMvc.perform(get(LOOKUP_URL)
                        .param("key", "missing-checkout-key")
                        .param("userId", "10")
                        .header(SECRET_HEADER, INTERNAL_SECRET))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(
                        "No payment found for the supplied checkout key"));
    }

    @Test
    void createPayment_withoutSecret_returnsUnauthorized() throws Exception {
        mockMvc.perform(post(PAYMENT_URL)
                        .header("Idempotency-Key", "checkout-test-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequestJson()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService);
    }

    @Test
    void createPayment_withValidSecretCreatesPayment() throws Exception {
        PaymentResponse payment = paymentResponse();
        when(paymentService.createPayment(any(), eq("checkout-test-123")))
                .thenReturn(payment);

        mockMvc.perform(post(PAYMENT_URL)
                        .header("Idempotency-Key", "checkout-test-123")
                        .header(SECRET_HEADER, INTERNAL_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(22))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(paymentService).createPayment(any(), eq("checkout-test-123"));
    }

    @Test
    void getPayment_withoutSecret_returnsUnauthorized() throws Exception {
        mockMvc.perform(get(PAYMENT_URL + "/5").param("userId", "10"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(paymentService);
    }

    @Test
    void getPayment_withValidSecretReturnsPayment() throws Exception {
        when(paymentService.getPayment(5L, 10L)).thenReturn(paymentResponse());

        mockMvc.perform(get(PAYMENT_URL + "/5")
                        .param("userId", "10")
                        .header(SECRET_HEADER, INTERNAL_SECRET))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.orderId").value(22));

        verify(paymentService).getPayment(5L, 10L);
    }

    private PaymentResponse paymentResponse() {
        return new PaymentResponse(
                5L,
                22L,
                10L,
                new BigDecimal("500.00"),
                PaymentMethod.UPI,
                PaymentStatus.SUCCESS,
                Instant.parse("2026-10-24T04:00:00Z"),
                Instant.parse("2026-10-24T04:00:00Z")
        );
    }

    private String paymentRequestJson() {
        return """
                {"orderId":22,"userId":10,"amount":500.00,"paymentMethod":"UPI"}
                """;
    }
}
