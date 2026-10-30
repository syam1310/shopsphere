package com.shopsphere.notification_service.controller;

import com.shopsphere.notification_service.dto.SendEmailRequest;
import com.shopsphere.notification_service.service.EmailService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/notifications/email")
public class EmailController {

    private final EmailService emailService;
    private final String internalSecret;

    public EmailController(
            EmailService emailService,
            @Value("${app.internal-service-secret:}") String internalSecret) {
        this.emailService = emailService;
        this.internalSecret = internalSecret;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sendEmail(
            @RequestHeader(value = "X-Internal-Secret", required = false) String suppliedSecret,
            @Valid @RequestBody SendEmailRequest request) {

        if (internalSecret == null || internalSecret.isBlank()
                || suppliedSecret == null
                || !MessageDigest.isEqual(
                        internalSecret.getBytes(StandardCharsets.UTF_8),
                        suppliedSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Invalid internal service credentials");
        }

        emailService.send(request.to(), request.subject(), request.message());
    }
}
