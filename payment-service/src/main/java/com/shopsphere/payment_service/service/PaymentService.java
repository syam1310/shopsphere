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

        var existing = paymentRepository
                .findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            Payment payment = existing.get();

            if (!payment.getOrderId().equals(request.orderId())
                    || !payment.getUserId().equals(request.userId())
                    || payment.getAmount().compareTo(request.amount()) != 0
                    || payment.getPaymentMethod() != request.paymentMethod()) {
                throw new IllegalArgumentException(
                        "Idempotency-Key was already used with different payment details");
            }

            return PaymentResponse.from(payment);
        }

        Payment payment = new Payment();
        payment.setOrderId(request.orderId());
        payment.setUserId(request.userId());
        payment.setAmount(request.amount());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setIdempotencyKey(idempotencyKey);

        // Temporary simulation rule for local development only.
        payment.setStatus(
                request.amount().compareTo(new BigDecimal("1000.00")) < 0
                        ? PaymentStatus.SUCCESS
                        : PaymentStatus.FAILED
        );

        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long paymentId, Long userId) {
        return paymentRepository.findByIdAndUserId(paymentId, userId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    public static class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException(Long paymentId) {
            super("Payment not found: " + paymentId);
        }
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

}
