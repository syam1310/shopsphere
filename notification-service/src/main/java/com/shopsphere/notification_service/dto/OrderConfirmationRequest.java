package com.shopsphere.notification_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderConfirmationRequest(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotBlank @Email String email,
        @NotNull BigDecimal totalAmount
) {
}
