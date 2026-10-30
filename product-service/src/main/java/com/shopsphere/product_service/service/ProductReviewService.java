package com.shopsphere.product_service.service;

import com.shopsphere.product_service.dto.ProductReviewRequest;
import com.shopsphere.product_service.dto.ProductReviewResponse;
import com.shopsphere.product_service.enity.Product;
import com.shopsphere.product_service.entity.ProductReview;
import com.shopsphere.product_service.exception.ProductNotFoundException;
import com.shopsphere.product_service.repository.ProductRepository;
import com.shopsphere.product_service.repository.ProductReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class ProductReviewService {
    private final ProductRepository products;
    private final ProductReviewRepository reviews;

    public ProductReviewService(ProductRepository products, ProductReviewRepository reviews) {
        this.products = products;
        this.reviews = reviews;
    }

    @Transactional(readOnly = true)
    public List<ProductReviewResponse> list(Long productId) {
        if (!products.existsById(productId)) throw new ProductNotFoundException("Product not found with id: " + productId);
        return reviews.findByProductIdOrderByCreatedAtDesc(productId).stream().map(this::response).toList();
    }

    @Transactional
    public ProductReviewResponse create(Long productId, String username, ProductReviewRequest request) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));
        String reviewer = username.trim().toLowerCase(Locale.ROOT);
        if (reviews.existsByProductIdAndReviewerUsername(productId, reviewer)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this product");
        }
        ProductReview review = new ProductReview();
        review.setProduct(product);
        review.setReviewerUsername(reviewer);
        review.setRating(request.rating());
        review.setComment(request.comment().trim());
        return response(reviews.save(review));
    }

    private ProductReviewResponse response(ProductReview review) {
        String username = review.getReviewerUsername();
        String displayName = username.contains("@") ? username.substring(0, username.indexOf('@')) : username;
        return new ProductReviewResponse(review.getId(), review.getProduct().getId(), displayName,
                review.getRating(), review.getComment(), review.getCreatedAt());
    }
}
