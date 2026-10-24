package com.shopsphere.payment_service.controller;

import com.shopsphere.payment_service.dto.CreatePaymentRequest;
import com.shopsphere.payment_service.dto.PaymentResponse;
import com.shopsphere.payment_service.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        PaymentResponse response =
                paymentService.createPayment(request, idempotencyKey);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable Long id,
            @RequestParam Long userId) {

        return ResponseEntity.ok(
                paymentService.getPayment(id, userId));
    }


    @GetMapping("/internal/by-idempotency-key")
    public ResponseEntity<PaymentResponse> getPaymentByIdempotencyKey(
            @RequestParam String key,
            @RequestParam Long userId) {

        PaymentResponse response = paymentService
                .getPaymentByIdempotencyKey(key, userId);

        return ResponseEntity.ok(response);
    }

}
