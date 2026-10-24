
package com.shopsphere.cart_service.controller;

import com.shopsphere.cart_service.dto.AddCartItemRequest;
import com.shopsphere.cart_service.dto.CartResponse;
import com.shopsphere.cart_service.dto.UpdateCartItemRequest;
import com.shopsphere.cart_service.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartResponse> getCart(
            Authentication authentication) {

        Long userId = getAuthenticatedUserId(authentication);

        return ResponseEntity.ok(cartService.getCart(userId));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            Authentication authentication,
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody AddCartItemRequest request) {

        Long userId = getAuthenticatedUserId(authentication);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cartService.addItem(userId, request, authorization));
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItem(
            Authentication authentication,
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long productId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        Long userId = getAuthenticatedUserId(authentication);

        return ResponseEntity.ok(
                cartService.updateItem(
                        userId, productId, request, authorization
                )
        );
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItem(
            Authentication authentication,
            @PathVariable Long productId) {

        Long userId = getAuthenticatedUserId(authentication);

        cartService.removeItem(userId, productId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(
            Authentication authentication) {

        Long userId = getAuthenticatedUserId(authentication);

        cartService.clearCart(userId);

        return ResponseEntity.noContent().build();
    }

    private Long getAuthenticatedUserId(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Authenticated user ID is unavailable"
            );
        }

        return userId;
    }
}
