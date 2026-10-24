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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "internal-service.secret=test-internal-secret")
@AutoConfigureMockMvc
class PaymentServiceApplicationTests {

    private static final String INTERNAL_SECRET = "test-internal-secret";
    private static final String LOOKUP_URL =
            "/api/payments/internal/by-idempotency-key";

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
                        .header("X-Internal-Service-Secret", INTERNAL_SECRET))
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
                        .header("X-Internal-Service-Secret", "wrong-secret"))
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
                        .header("X-Internal-Service-Secret", INTERNAL_SECRET))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(
                        "No payment found for the supplied checkout key"));
    }
}