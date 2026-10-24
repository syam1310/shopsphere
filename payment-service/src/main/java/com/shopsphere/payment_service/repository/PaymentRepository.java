package com.shopsphere.payment_service.repository;

import com.shopsphere.payment_service.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByIdAndUserId(Long id, Long userId);

    boolean existsByOrderIdAndUserId(Long orderId, Long userId);
}
