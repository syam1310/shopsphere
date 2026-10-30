package com.shopsphere.payment_service.service;

import com.shopsphere.payment_service.dto.CreatePaymentRequest;
import com.shopsphere.payment_service.dto.PaymentResponse;
import com.shopsphere.payment_service.entity.Payment;
import com.shopsphere.payment_service.entity.PaymentMethod;
import com.shopsphere.payment_service.entity.PaymentStatus;
import com.shopsphere.payment_service.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String KEY = "checkout-test-123";
    private static final Long ORDER_ID = 22L;
    private static final Long USER_ID = 10L;
    private static final BigDecimal AMOUNT = new BigDecimal("500.00");

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void createPayment_whenSameKeyAndDetailsExist_returnsExistingPayment() {
        Payment existing = payment(PaymentStatus.SUCCESS);

        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.createPayment(
                request(AMOUNT, PaymentMethod.UPI), KEY);


        assertEquals(ORDER_ID, response.orderId());
        assertEquals(PaymentStatus.SUCCESS, response.status());

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPayment_whenSameKeyHasDifferentAmount_rejectsRequest() {
        Payment existing = payment(PaymentStatus.SUCCESS);

        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> paymentService.createPayment(
                        request(new BigDecimal("700.00"),
                                PaymentMethod.UPI), KEY));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPayment_whenKeyIsNew_savesPaymentOnce() {
        when(paymentRepository.insertIfIdempotencyKeyAbsent(
                eq(ORDER_ID),
                eq(USER_ID),
                eq(AMOUNT),
                eq(PaymentMethod.UPI.name()),
                eq(PaymentStatus.SUCCESS.name()),
                eq(KEY)))
                .thenReturn(1);

        Payment savedPayment = payment(PaymentStatus.SUCCESS);

        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(savedPayment));

        PaymentResponse response = paymentService.createPayment(
                request(AMOUNT, PaymentMethod.UPI), KEY);

        assertEquals(ORDER_ID, response.orderId());
        assertEquals(USER_ID, response.userId());
        assertEquals(AMOUNT, response.amount());
        assertEquals(PaymentMethod.UPI, response.paymentMethod());
        assertEquals(PaymentStatus.SUCCESS, response.status());

        verify(paymentRepository).insertIfIdempotencyKeyAbsent(
                eq(ORDER_ID),
                eq(USER_ID),
                eq(AMOUNT),
                eq(PaymentMethod.UPI.name()),
                eq(PaymentStatus.SUCCESS.name()),
                eq(KEY));

        verify(paymentRepository).findByIdempotencyKey(KEY);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void createPayment_whenAmountIsAboveDemoThreshold_stillSimulatesSuccess() {
        BigDecimal highAmount = new BigDecimal("25000.00");
        when(paymentRepository.insertIfIdempotencyKeyAbsent(
                eq(ORDER_ID),
                eq(USER_ID),
                eq(highAmount),
                eq(PaymentMethod.CARD.name()),
                eq(PaymentStatus.SUCCESS.name()),
                eq(KEY)))
                .thenReturn(1);

        Payment payment = payment(PaymentStatus.SUCCESS);
        payment.setAmount(highAmount);
        payment.setPaymentMethod(PaymentMethod.CARD);
        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.createPayment(
                request(highAmount, PaymentMethod.CARD), KEY);

        assertEquals(PaymentStatus.SUCCESS, response.status());
        assertEquals(highAmount, response.amount());
        verify(paymentRepository).insertIfIdempotencyKeyAbsent(
                eq(ORDER_ID), eq(USER_ID), eq(highAmount),
                eq(PaymentMethod.CARD.name()), eq(PaymentStatus.SUCCESS.name()), eq(KEY));
    }

    @Test
    void createPayment_whenSameKeyHasDifferentPaymentMethod_rejectsRequest() {
        Payment existing = payment(PaymentStatus.SUCCESS);

        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class,
                () -> paymentService.createPayment(
                        request(AMOUNT, PaymentMethod.CARD), KEY));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    private CreatePaymentRequest request(
            BigDecimal amount, PaymentMethod method) {
        return new CreatePaymentRequest(ORDER_ID, USER_ID, amount, method);
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = new Payment();
        payment.setOrderId(ORDER_ID);
        payment.setUserId(USER_ID);
        payment.setAmount(AMOUNT);
        payment.setPaymentMethod(PaymentMethod.UPI);
        payment.setStatus(status);
        payment.setIdempotencyKey(KEY);
        return payment;
    }

    @Test
    void createPayment_whenInsertConflicts_returnsExistingPayment() {
        when(paymentRepository.insertIfIdempotencyKeyAbsent(
                eq(ORDER_ID),
                eq(USER_ID),
                eq(AMOUNT),
                eq(PaymentMethod.UPI.name()),
                eq(PaymentStatus.SUCCESS.name()),
                eq(KEY)))
                .thenReturn(0);

        Payment existing = payment(PaymentStatus.SUCCESS);

        when(paymentRepository.findByIdempotencyKey(KEY))
                .thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.createPayment(
                request(AMOUNT, PaymentMethod.UPI), KEY);

        assertEquals(ORDER_ID, response.orderId());
        assertEquals(USER_ID, response.userId());
        assertEquals(PaymentStatus.SUCCESS, response.status());

        verify(paymentRepository).insertIfIdempotencyKeyAbsent(
                eq(ORDER_ID),
                eq(USER_ID),
                eq(AMOUNT),
                eq(PaymentMethod.UPI.name()),
                eq(PaymentStatus.SUCCESS.name()),
                eq(KEY));
        verify(paymentRepository).findByIdempotencyKey(KEY);
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    private static class TestPayment {
        private final Payment payment;

        private TestPayment(Payment payment, Long id) {
            this.payment = payment;
            // Payment intentionally exposes no ID setter.
        }
    }

}
