package com.shopsphere.product_service.repository;

import com.shopsphere.product_service.entity.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    List<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId);
    boolean existsByProductIdAndReviewerUsername(Long productId, String reviewerUsername);
}
