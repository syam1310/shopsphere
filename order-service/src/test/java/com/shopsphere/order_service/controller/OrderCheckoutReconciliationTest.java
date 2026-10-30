package com.shopsphere.order_service.controller;

import com.shopsphere.order_service.client.CartClient;
import com.shopsphere.order_service.client.NotificationClient;
import com.shopsphere.order_service.client.PaymentClient;
import com.shopsphere.order_service.client.UserClient;
import com.shopsphere.order_service.entity.CheckoutRequest;
import com.shopsphere.order_service.entity.CheckoutStatus;
import com.shopsphere.order_service.entity.Order;
import com.shopsphere.order_service.entity.OrderStatus;
import com.shopsphere.order_service.security.JwtAuthenticationFilter;
import com.shopsphere.order_service.security.JwtService;
import com.shopsphere.order_service.service.CheckoutIdempotencyService;
import com.shopsphere.order_service.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        com.shopsphere.order_service.exception.GlobalExceptionHandler.class,
        OrderCheckoutReconciliationTest.TestSecurityConfig.class
})
class OrderCheckoutReconciliationTest {

    private static final Long USER_ID = 10L;
    private static final Long ORDER_ID = 22L;
    private static final String KEY = "reconciliation-test-key";
    private static final String AUTH_HEADER = "Bearer test-token";
    private static final BigDecimal AMOUNT = new BigDecimal("500.00");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CartClient cartClient;

    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private NotificationClient notificationClient;

    @MockitoBean
    private PaymentClient paymentClient;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @MockitoBean
    private CheckoutIdempotencyService checkoutIdempotencyService;

    private Order pendingOrder() {
        return new Order(
                ORDER_ID,
                USER_ID,
                KEY,
                AMOUNT,
                OrderStatus.PENDING,
                null,
                null,
                new java.util.ArrayList<>()
        );
    }

    private PaymentClient.PaymentResponse payment(String paymentStatus) {
        return new PaymentClient.PaymentResponse(
                5L,
                ORDER_ID,
                USER_ID,
                AMOUNT,
                "UPI",
                paymentStatus,
                null,
                null
        );
    }

    private void arrangePendingOrder() {
        when(checkoutIdempotencyService.claim(USER_ID, KEY))
                .thenReturn(false);

        when(orderService.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(pendingOrder()));
    }

    private MockHttpServletRequestBuilder checkoutRequest() {
        var auth = new UsernamePasswordAuthenticationToken(
                USER_ID,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        return post("/api/orders/checkout")
                .principal(auth)
                .header("Authorization", AUTH_HEADER)
                .header("Idempotency-Key", KEY)
                .param("paymentMethod", "UPI")
                .contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder cancelOrderRequest() {
        var auth = new UsernamePasswordAuthenticationToken(
                USER_ID,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        return patch("/api/orders/{id}/status", ORDER_ID)
                .principal(auth)
                .param("status", "CANCELLED");
    }

    @Test
    void cancellationWhileCheckoutIsProcessing_returnsConflictWithoutCancellingOrder()
            throws Exception {
        Order order = pendingOrder();
        CheckoutRequest checkout = new CheckoutRequest();
        checkout.setStatus(CheckoutStatus.PROCESSING);

        when(orderService.getOrderById(ORDER_ID)).thenReturn(Optional.of(order));
        when(checkoutIdempotencyService.find(USER_ID, KEY))
                .thenReturn(Optional.of(checkout));

        mockMvc.perform(cancelOrderRequest())
                .andExpect(status().isConflict());

        verify(orderService, never()).updateOrderStatus(
                ORDER_ID, OrderStatus.CANCELLED);
    }

    @Test
    void failedPayment_cancelsOrderAndReturns402() throws Exception {
        arrangePendingOrder();

        when(paymentClient.createPayment(any(PaymentClient.CreatePaymentRequest.class), eq(KEY)))
                .thenReturn(payment("FAILED"));

        Order cancelledOrder = pendingOrder();
        cancelledOrder.setStatus(OrderStatus.CANCELLED);

        when(orderService.updateOrderStatus(ORDER_ID, OrderStatus.CANCELLED))
                .thenReturn(cancelledOrder);

        mockMvc.perform(checkoutRequest())
                .andExpect(status().isPaymentRequired());

        verify(orderService).updateOrderStatus(
                ORDER_ID, OrderStatus.CANCELLED);

        verify(checkoutIdempotencyService).markFailed(USER_ID, KEY);

        verify(orderService, never()).updateOrderStatus(
                ORDER_ID, OrderStatus.CONFIRMED);

        verify(cartClient, never()).clearCart(anyString());
    }

    @Test
    void successfulPayment_confirmsOrderAndClearsCart() throws Exception {
        arrangePendingOrder();

        when(paymentClient.createPayment(any(PaymentClient.CreatePaymentRequest.class), eq(KEY)))
                .thenReturn(payment("SUCCESS"));

        Order confirmedOrder = pendingOrder();
        confirmedOrder.setStatus(OrderStatus.CONFIRMED);

        when(orderService.updateOrderStatus(ORDER_ID, OrderStatus.CONFIRMED))
                .thenReturn(confirmedOrder);

        mockMvc.perform(checkoutRequest())
                .andExpect(status().isOk());

        verify(orderService).updateOrderStatus(
                ORDER_ID, OrderStatus.CONFIRMED);

        verify(checkoutIdempotencyService).markCompleted(
                USER_ID, KEY, ORDER_ID);

        verify(cartClient).clearCart(AUTH_HEADER);
    }

    @Test
    void pendingOrderWithoutPayment_retriesCreationWithExistingCheckoutKey()
            throws Exception {
        arrangePendingOrder();

        when(paymentClient.createPayment(any(PaymentClient.CreatePaymentRequest.class), eq(KEY)))
                .thenReturn(payment("SUCCESS"));

        Order confirmedOrder = pendingOrder();
        confirmedOrder.setStatus(OrderStatus.CONFIRMED);
        when(orderService.updateOrderStatus(ORDER_ID, OrderStatus.CONFIRMED))
                .thenReturn(confirmedOrder);

        mockMvc.perform(checkoutRequest())
                .andExpect(status().isOk());

        verify(paymentClient).createPayment(
                new PaymentClient.CreatePaymentRequest(
                        ORDER_ID, USER_ID, AMOUNT, "UPI"),
                KEY);
        verify(orderService).updateOrderStatus(ORDER_ID, OrderStatus.CONFIRMED);
        verify(checkoutIdempotencyService).markCompleted(USER_ID, KEY, ORDER_ID);
        verify(cartClient).clearCart(AUTH_HEADER);
    }

    @Test
    void paymentServiceUnavailable_returns503AndLeavesOrderPending()
            throws Exception {
        arrangePendingOrder();

        when(paymentClient.createPayment(any(PaymentClient.CreatePaymentRequest.class), eq(KEY)))
                .thenThrow(new PaymentClient.PaymentServiceException(
                        "Payment service unavailable",
                        new IllegalStateException("Connection failed")
                ));

        mockMvc.perform(checkoutRequest())
                .andExpect(status().isServiceUnavailable());

        verify(orderService, never()).updateOrderStatus(
                eq(ORDER_ID), any(OrderStatus.class));

        verify(checkoutIdempotencyService, never())
                .markCompleted(anyLong(), anyString(), anyLong());

        verify(checkoutIdempotencyService, never())
                .markFailed(anyLong(), anyString());

        verify(cartClient, never()).clearCart(anyString());
    }

    @Test
    void mismatchedPaymentDetails_returns409WithoutConfirmingOrder()
            throws Exception {
        arrangePendingOrder();

        PaymentClient.PaymentResponse mismatchedPayment =
                new PaymentClient.PaymentResponse(
                        5L,
                        999L,
                        USER_ID,
                        AMOUNT,
                        "UPI",
                        "SUCCESS",
                        null,
                        null
                );

        when(paymentClient.createPayment(any(PaymentClient.CreatePaymentRequest.class), eq(KEY)))
                .thenReturn(mismatchedPayment);

        mockMvc.perform(checkoutRequest())
                .andExpect(status().isConflict());

        verify(orderService, never()).updateOrderStatus(
                ORDER_ID, OrderStatus.CONFIRMED);

        verify(checkoutIdempotencyService, never())
                .markCompleted(anyLong(), anyString(), anyLong());

        verify(cartClient, never()).clearCart(anyString());
    }

    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http)
                throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

            return http.build();
        }
    }
}
