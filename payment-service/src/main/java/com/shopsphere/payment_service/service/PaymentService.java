package com.shopsphere.payment_service.service;

import com.shopsphere.payment_service.dto.CreatePaymentRequest;
import com.shopsphere.payment_service.dto.PaymentResponse;
import com.shopsphere.payment_service.entity.Payment;
import com.shopsphere.payment_service.entity.PaymentStatus;
import com.shopsphere.payment_service.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }


    @Transactional
    public PaymentResponse createPayment(
            CreatePaymentRequest request,
            String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key header is required");
        }

        if (idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must not exceed 100 characters");
        }

        if (request == null
                || request.orderId() == null
                || request.userId() == null
                || request.amount() == null
                || request.amount().signum() <= 0
                || request.paymentMethod() == null) {
            throw new IllegalArgumentException(
                    "Valid payment details are required");
        }

        PaymentStatus status =
                request.amount().compareTo(new BigDecimal("1000.00")) < 0
                        ? PaymentStatus.SUCCESS
                        : PaymentStatus.FAILED;

        paymentRepository.insertIfIdempotencyKeyAbsent(
                request.orderId(),
                request.userId(),
                request.amount(),
                request.paymentMethod().name(),
                status.name(),
                idempotencyKey);

        Payment payment = paymentRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Payment was not found after the idempotent insert"));

        if (!payment.getOrderId().equals(request.orderId())
                || !payment.getUserId().equals(request.userId())
                || payment.getAmount().compareTo(request.amount()) != 0
                || payment.getPaymentMethod() != request.paymentMethod()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key was already used with different payment details");
        }

        return PaymentResponse.from(payment);
    }


    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId, Long userId) {
        return paymentRepository.findByIdAndUserId(paymentId, userId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByIdempotencyKey(
            String idempotencyKey,
            Long userId) {

        if (idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException(
                    "A valid idempotency key is required");
        }

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "A valid user ID is required");
        }

        return paymentRepository
                .findByIdempotencyKeyAndUserId(idempotencyKey, userId)
                .map(PaymentResponse::from)
                .orElseThrow(() ->
                        new PaymentNotFoundException(
                                "No payment found for the supplied checkout key"));
    }

    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(Long paymentId) {
            super("Payment not found: " + paymentId);
        }

        public PaymentNotFoundException(String msg) {
            super(msg);
        }
    }

}
