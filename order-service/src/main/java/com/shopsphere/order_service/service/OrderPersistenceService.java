package com.shopsphere.order_service.service;

import com.shopsphere.order_service.entity.Order;
import com.shopsphere.order_service.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps order persistence in its own transaction, outside remote inventory calls. */
@Service
public class OrderPersistenceService {

    private final OrderRepository orderRepository;

    public OrderPersistenceService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order save(Order order) {
        return orderRepository.saveAndFlush(order);
    }
}
