package com.shopsphere.payment_service.dto;

import com.shopsphere.payment_service.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreatePaymentRequest(
        @NotNull @Positive Long orderId,
        @NotNull @Positive Long userId,
        @NotNull @Positive BigDecimal amount,
        @NotNull PaymentMethod paymentMethod
) {}
