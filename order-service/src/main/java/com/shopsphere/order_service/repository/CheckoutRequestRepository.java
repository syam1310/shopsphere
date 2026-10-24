package com.shopsphere.order_service.repository;

import com.shopsphere.order_service.entity.CheckoutRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CheckoutRequestRepository
        extends JpaRepository<CheckoutRequest, Long> {

    Optional<CheckoutRequest> findByUserIdAndIdempotencyKey(
            Long userId,
            String idempotencyKey
    );

    @Modifying
    @Query(value = """
            INSERT INTO checkout_requests
                (user_id, idempotency_key, status, created_at, updated_at)
            VALUES
                (:userId, :key, 'PROCESSING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int claimCheckoutKey(
            @Param("userId") Long userId,
            @Param("key") String idempotencyKey
    );
}
