package com.shopsphere.order_service.repository;

import com.shopsphere.order_service.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);
    Optional<Order> findByUserIdAndIdempotencyKey(
            Long userId,
            String idempotencyKey
    );
}
