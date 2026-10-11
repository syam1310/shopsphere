package com.shopsphere.order_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateOrderRequest(

        @NotEmpty(message = "An order must contain at least one item")
        List<@Valid OrderItemRequest> items

) {
}
