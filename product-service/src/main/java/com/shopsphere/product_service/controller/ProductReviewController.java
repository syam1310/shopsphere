package com.shopsphere.product_service.controller;

import com.shopsphere.product_service.dto.ProductReviewRequest;
import com.shopsphere.product_service.dto.ProductReviewResponse;
import com.shopsphere.product_service.service.ProductReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/reviews")
public class ProductReviewController {
    private final ProductReviewService reviews;

    public ProductReviewController(ProductReviewService reviews) { this.reviews = reviews; }

    @GetMapping
    public List<ProductReviewResponse> list(@PathVariable Long productId) {
        return reviews.list(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductReviewResponse create(@PathVariable Long productId,
                                        @Valid @RequestBody ProductReviewRequest request,
                                        Authentication authentication) {
        return reviews.create(productId, authentication.getName(), request);
    }
}
