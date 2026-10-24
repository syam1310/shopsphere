package com.shopsphere.payment_service.repository;

import com.shopsphere.payment_service.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByIdAndUserId(Long id, Long userId);

    Optional<Payment> findByIdempotencyKeyAndUserId(
            String idempotencyKey,
            Long userId);

    boolean existsByOrderIdAndUserId(Long orderId, Long userId);

    @Modifying
    @Query(value = """
            INSERT INTO payments
                (order_id, user_id, amount, payment_method, status,
                 idempotency_key, created_at, updated_at)
            VALUES
                (:orderId, :userId, :amount, :paymentMethod, :status,
                 :idempotencyKey, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfIdempotencyKeyAbsent(
            @Param("orderId") Long orderId,
            @Param("userId") Long userId,
            @Param("amount") BigDecimal amount,
            @Param("paymentMethod") String paymentMethod,
            @Param("status") String status,
            @Param("idempotencyKey") String idempotencyKey);
}
