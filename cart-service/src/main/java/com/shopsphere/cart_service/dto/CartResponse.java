
package com.shopsphere.cart_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CartResponse(
        Long cartId,
        Long userId,
        List<CartItemResponse> items,
        BigDecimal total,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
