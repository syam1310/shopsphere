package com.shopsphere.inventory_service.dto;

public record InventoryAvailabilityResponse(
        Long productId,
        Integer availableQuantity
) {
}
