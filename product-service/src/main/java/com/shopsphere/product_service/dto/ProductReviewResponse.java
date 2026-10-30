package com.shopsphere.product_service.dto;

import java.time.Instant;

public record ProductReviewResponse(
        Long id,
        Long productId,
        String reviewerName,
        int rating,
        String comment,
        Instant createdAt
) { }
