package com.shopsphere.notification_service.controller;

import com.shopsphere.notification_service.dto.OrderConfirmationRequest;
import com.shopsphere.notification_service.service.OrderEmailQueueService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/notifications/orders")
public class OrderEmailController {

    private final OrderEmailQueueService queueService;
    private final String internalSecret;

    public OrderEmailController(
            OrderEmailQueueService queueService,
            @Value("${app.internal-service-secret:}") String internalSecret) {
        this.queueService = queueService;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/confirmed")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void confirmed(
            @RequestHeader(value = "X-Internal-Secret", required = false)
            String suppliedSecret,
            @Valid @RequestBody OrderConfirmationRequest request) {

        if (internalSecret == null || internalSecret.isBlank()
                || suppliedSecret == null
                || !MessageDigest.isEqual(
                internalSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Invalid internal credentials");
        }

        queueService.enqueue(request);
    }
}
