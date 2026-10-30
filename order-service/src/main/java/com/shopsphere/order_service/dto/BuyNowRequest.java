package com.shopsphere.order_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BuyNowRequest(
        @NotNull @Positive Long productId,
        @NotNull @Positive Integer quantity
) { }
