package com.shopsphere.order_service.service;

import com.shopsphere.order_service.client.InventoryClient;
import com.shopsphere.order_service.client.ProductClient;
import com.shopsphere.order_service.client.ProductClient.ProductResponse;
import com.shopsphere.order_service.dto.CreateOrderRequest;
import com.shopsphere.order_service.dto.OrderItemRequest;
import com.shopsphere.order_service.entity.Order;
import com.shopsphere.order_service.entity.OrderItem;
import com.shopsphere.order_service.entity.OrderStatus;
import com.shopsphere.order_service.exception.InvalidOrderStatusException;
import com.shopsphere.order_service.exception.OrderNotFoundException;
import com.shopsphere.order_service.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final InventoryCompensationService compensationService;

    public OrderService(
            OrderRepository orderRepository,
            ProductClient productClient,
            InventoryClient inventoryClient,
            InventoryCompensationService compensationService
    ) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.inventoryClient = inventoryClient;
        this.compensationService = compensationService;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    public List<Order> getOrdersByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }


    @Transactional
    public Order createOrder(
            CreateOrderRequest request,
            Long userId,
            String token,
            String idempotencyKey) {

        return createOrderInternal(request, userId, token, idempotencyKey);
    }

    @Transactional
    public Order createOrder(
            CreateOrderRequest request,
            Long userId,
            String token) {

        return createOrderInternal(request, userId, token, null);
    }

    private Order createOrderInternal(
            CreateOrderRequest request,
            Long userId,
            String token,
            String idempotencyKey) {

        BigDecimal totalAmount = BigDecimal.ZERO;
        Order order = new Order();
        order.setUserId(userId);
        order.setIdempotencyKey(idempotencyKey);

        List<OrderItem> reservedItems = new ArrayList<>();

        try {
            for (OrderItemRequest item : request.items()) {
                ProductResponse product =
                        productClient.getProductById(item.productId(), token);

                if (product == null || product.id() == null) {
                    throw new IllegalStateException(
                            "Product service returned an invalid response");
                }

                if (!product.active()) {
                    throw new IllegalArgumentException(
                            "Product " + product.id() + " is inactive");
                }

                if (product.price() == null || product.price().signum() < 0) {
                    throw new IllegalStateException(
                            "Product service returned an invalid price");
                }

                if (product.quantity() == null
                        || item.quantity() > product.quantity()) {
                    throw new IllegalArgumentException(
                            "Insufficient stock for product " + product.id());
                }

                BigDecimal itemTotal = product.price()
                        .multiply(BigDecimal.valueOf(item.quantity()));

                UUID reservationId = UUID.randomUUID();

                OrderItem orderItem = new OrderItem();
                orderItem.setReservationId(reservationId);
                orderItem.setProductId(product.id());
                orderItem.setQuantity(item.quantity());
                orderItem.setUnitPrice(product.price());
                orderItem.setSubtotal(itemTotal);

                order.addItem(orderItem);
                totalAmount = totalAmount.add(itemTotal);

                try {
                    inventoryClient.reserveInventory(
                            reservationId,
                            product.id(),
                            item.quantity());
                    reservedItems.add(orderItem);
                } catch (HttpClientErrorException exception) {
                    // A definitive 4xx response means Inventory rejected the
                    // reservation; there is nothing to release for this item.
                    throw exception;
                } catch (RuntimeException exception) {
                    // A timeout or 5xx may occur after Inventory committed the
                    // reservation, so keep this UUID for compensating release.
                    reservedItems.add(orderItem);
                    throw exception;
                }
            }

            order.setTotalAmount(totalAmount);
            order.setStatus(OrderStatus.PENDING);

            // Save the order with its idempotency key on the first save.
            return orderRepository.save(order);

        } catch (RuntimeException exception) {
            for (OrderItem reservedItem : reservedItems) {
                try {
                    inventoryClient.releaseInventory(
                            reservedItem.getReservationId(),
                            reservedItem.getProductId(),
                            reservedItem.getQuantity());

                } catch (RuntimeException releaseException) {
                    exception.addSuppressed(releaseException);

                    try {
                        compensationService.recordFailedRelease(
                                reservedItem.getReservationId(),
                                reservedItem.getProductId(),
                                reservedItem.getQuantity(),
                                releaseException);

                    } catch (RuntimeException persistenceException) {
                        exception.addSuppressed(persistenceException);
                    }
                }
            }

            throw exception;
        }
    }


    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Cancellation is idempotent: retries must not release the same stock again.
        if (newStatus == OrderStatus.CANCELLED
                && order.getStatus() == OrderStatus.CANCELLED) {
            return order;
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStatusException(
                    "Only pending orders can be confirmed or cancelled");
        }

        if (newStatus != OrderStatus.CONFIRMED
                && newStatus != OrderStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "Only CONFIRMED or CANCELLED status is allowed");
        }


        if (newStatus == OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                try {
                    inventoryClient.releaseInventory(
                            item.getReservationId(),
                            item.getProductId(),
                            item.getQuantity());

                } catch (RuntimeException releaseException) {

                    // Legacy items have no reservation ID, so they cannot
                    // be safely queued in the reservation-based retry system.
                    if (item.getReservationId() == null) {
                        throw releaseException;
                    }

                    try {
                        // Persist recovery in a separate transaction.
                        compensationService.recordFailedRelease(
                                item.getReservationId(),
                                item.getProductId(),
                                item.getQuantity(),
                                releaseException);

                        // Continue cancellation only after recording succeeds.
                    } catch (RuntimeException persistenceException) {
                        releaseException.addSuppressed(persistenceException);

                        // Recovery was not durably recorded; fail visibly.
                        throw releaseException;
                    }
                }
            }
        }


        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    public Optional<Order> findByUserIdAndIdempotencyKey(
            Long userId,
            String idempotencyKey) {
        return orderRepository.findByUserIdAndIdempotencyKey(
                userId, idempotencyKey);
    }
}
