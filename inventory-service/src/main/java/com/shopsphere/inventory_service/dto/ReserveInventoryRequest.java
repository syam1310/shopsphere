package com.shopsphere.inventory_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record ReserveInventoryRequest(
        UUID reservationId,

        @NotNull
        @Positive
        Long productId,

        @NotNull
        @Positive
        Integer quantity
) {
}
