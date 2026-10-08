package com.shopsphere.product_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer quantity,
        String category,
        String imageUrl,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}