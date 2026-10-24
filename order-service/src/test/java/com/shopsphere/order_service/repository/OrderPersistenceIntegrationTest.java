package com.shopsphere.order_service.repository;

import com.shopsphere.order_service.entity.Order;
import com.shopsphere.order_service.entity.OrderItem;
import com.shopsphere.order_service.entity.OrderStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
@Testcontainers
class OrderPersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("order_test")
                    .withUsername("test")
                    .withPassword("test");
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                POSTGRES::getDriverClassName
        );
    }

    @Test
    void saveOrder_persistsOrderAndItems() {
        UUID reservationId = UUID.randomUUID();

        Order order = new Order();
        order.setUserId(9L);
        order.setTotalAmount(new BigDecimal("1000.00"));
        order.setStatus(OrderStatus.PENDING);

        OrderItem item = new OrderItem();
        item.setProductId(3L);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("500.00"));
        item.setSubtotal(new BigDecimal("1000.00"));
        item.setReservationId(reservationId);

        order.addItem(item);

        Order savedOrder = orderRepository.saveAndFlush(order);

        assertNotNull(savedOrder.getId());
        assertNotNull(savedOrder.getCreatedAt());
        assertNotNull(savedOrder.getUpdatedAt());
        assertEquals(OrderStatus.PENDING, savedOrder.getStatus());
        assertEquals(1, savedOrder.getItems().size());

        Long orderId = savedOrder.getId();

        // Clear the persistence context to verify data is read from the database.
        entityManager.clear();

        Order reloadedOrder = orderRepository.findById(orderId)
                .orElseThrow();

        assertEquals(9L, reloadedOrder.getUserId());
        assertEquals(
                new BigDecimal("1000.00"),
                reloadedOrder.getTotalAmount()
        );
        assertEquals(OrderStatus.PENDING, reloadedOrder.getStatus());
        assertEquals(1, reloadedOrder.getItems().size());

        OrderItem reloadedItem = reloadedOrder.getItems().get(0);

        assertEquals(3L, reloadedItem.getProductId());
        assertEquals(2, reloadedItem.getQuantity());
        assertEquals(
                new BigDecimal("500.00"),
                reloadedItem.getUnitPrice()
        );
        assertEquals(
                new BigDecimal("1000.00"),
                reloadedItem.getSubtotal()
        );
        assertEquals(reservationId, reloadedItem.getReservationId());
        assertEquals(orderId, reloadedItem.getOrder().getId());
    }
}
