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
import com.shopsphere.order_service.exception.ProductNotFoundException;
import com.shopsphere.order_service.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private InventoryCompensationService compensationService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_whenSecondProductIsMissing_releasesFirstReservation() {
        ProductResponse product = new ProductResponse(
                3L,
                "Wireless Mouse",
                new BigDecimal("500.00"),
                10,
                true
        );

        ProductNotFoundException originalException =
                new ProductNotFoundException(999999L);

        when(productClient.getProductById(3L, "test-token"))
                .thenReturn(product);

        when(productClient.getProductById(999999L, "test-token"))
                .thenThrow(originalException);

        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest(3L, 1),
                new OrderItemRequest(999999L, 1)
        ));

        ProductNotFoundException thrown = assertThrows(
                ProductNotFoundException.class,
                () -> orderService.createOrder(request, 9L, "test-token")
        );

        assertSame(originalException, thrown);

        ArgumentCaptor<UUID> reservationIdCaptor =
                ArgumentCaptor.forClass(UUID.class);

        verify(inventoryClient).reserveInventory(
                reservationIdCaptor.capture(),
                eq(3L),
                eq(1)
        );

        UUID reservationId = reservationIdCaptor.getValue();

        verify(inventoryClient).releaseInventory(
                eq(reservationId),
                eq(3L),
                eq(1)
        );

        verify(compensationService, never()).recordFailedRelease(
                any(UUID.class),
                eq(3L),
                eq(1),
                any(RuntimeException.class)
        );

        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_whenCompensationFails_preservesOriginalException() {
        ProductResponse product = new ProductResponse(
                3L,
                "Wireless Mouse",
                new BigDecimal("500.00"),
                10,
                true
        );

        ProductNotFoundException originalException =
                new ProductNotFoundException(999999L);

        RuntimeException releaseException =
                new RuntimeException("Inventory release failed");

        when(productClient.getProductById(3L, "test-token"))
                .thenReturn(product);

        when(productClient.getProductById(999999L, "test-token"))
                .thenThrow(originalException);

        doThrow(releaseException)
                .when(inventoryClient)
                .releaseInventory(any(UUID.class), eq(3L), eq(1));

        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest(3L, 1),
                new OrderItemRequest(999999L, 1)
        ));

        ProductNotFoundException thrown = assertThrows(
                ProductNotFoundException.class,
                () -> orderService.createOrder(request, 9L, "test-token")
        );

        assertSame(originalException, thrown);
        assertTrue(List.of(thrown.getSuppressed()).contains(releaseException));

        verify(inventoryClient).reserveInventory(
                any(UUID.class),
                eq(3L),
                eq(1)
        );

        verify(inventoryClient).releaseInventory(
                any(UUID.class),
                eq(3L),
                eq(1)
        );

        verify(compensationService).recordFailedRelease(
                any(UUID.class),
                eq(3L),
                eq(1),
                same(releaseException)
        );

        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_whenReservationResponseFails_releasesAttemptedReservation() {
        ProductResponse product = new ProductResponse(
                3L,
                "Wireless Mouse",
                new BigDecimal("500.00"),
                10,
                true
        );
        RuntimeException reservationFailure = new RuntimeException("Inventory timeout");
        when(productClient.getProductById(3L, "test-token")).thenReturn(product);
        doThrow(reservationFailure).when(inventoryClient)
                .reserveInventory(any(UUID.class), eq(3L), eq(1));

        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest(3L, 1)
        ));

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> orderService.createOrder(request, 9L, "test-token")
        );

        assertSame(reservationFailure, thrown);
        ArgumentCaptor<UUID> reservationIdCaptor =
                ArgumentCaptor.forClass(UUID.class);
        verify(inventoryClient).reserveInventory(
                reservationIdCaptor.capture(), eq(3L), eq(1));
        verify(inventoryClient).releaseInventory(
                reservationIdCaptor.getValue(), 3L, 1);
        verify(compensationService, never()).recordFailedRelease(
                any(UUID.class), eq(3L), eq(1), any(RuntimeException.class));
        verify(orderRepository, never()).save(any());
    }


    @Test
    void updateOrderStatus_whenCancellationReleaseFails_recordsCompensation() {
        UUID reservationId = UUID.randomUUID();

        Order order = new Order();
        order.setId(10L);
        order.setStatus(OrderStatus.PENDING);

        OrderItem item = new OrderItem();
        item.setReservationId(reservationId);
        item.setProductId(3L);
        item.setQuantity(1);
        order.addItem(item);

        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(order));

        RuntimeException releaseException =
                new RuntimeException("Inventory release failed");

        doThrow(releaseException)
                .when(inventoryClient)
                .releaseInventory(reservationId, 3L, 1);

        orderService.updateOrderStatus(
                10L,
                OrderStatus.CANCELLED
        );

        assertEquals(OrderStatus.CANCELLED, order.getStatus());

        verify(inventoryClient).releaseInventory(
                reservationId, 3L, 1
        );

        verify(compensationService).recordFailedRelease(
                reservationId,
                3L,
                1,
                releaseException
        );

        verify(orderRepository).save(order);
    }

    @Test
    void updateOrderStatus_whenCancellingPendingOrder_releasesInventoryAndSavesCancellation() {
        UUID reservationId = UUID.randomUUID();
        Order order = orderWithReservation(OrderStatus.PENDING, reservationId);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order result = orderService.updateOrderStatus(10L, OrderStatus.CANCELLED);

        assertSame(order, result);
        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(inventoryClient).releaseInventory(reservationId, 3L, 1);
        verify(orderRepository).save(order);
        verifyNoInteractions(compensationService);
    }

    @Test
    void updateOrderStatus_whenCancellationIsRepeated_doesNotReleaseOrSaveAgain() {
        UUID reservationId = UUID.randomUUID();
        Order order = orderWithReservation(OrderStatus.CANCELLED, reservationId);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        Order result = orderService.updateOrderStatus(10L, OrderStatus.CANCELLED);

        assertSame(order, result);
        verifyNoInteractions(inventoryClient, compensationService);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrderStatus_whenOrderWasAlreadyConfirmed_rejectsCancellationWithoutReleasingInventory() {
        UUID reservationId = UUID.randomUUID();
        Order order = orderWithReservation(OrderStatus.CONFIRMED, reservationId);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStatusException.class,
                () -> orderService.updateOrderStatus(10L, OrderStatus.CANCELLED));

        verifyNoInteractions(inventoryClient, compensationService);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrderStatus_whenOrderDoesNotExist_returnsNotFoundWithoutReleasingInventory() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class,
                () -> orderService.updateOrderStatus(404L, OrderStatus.CANCELLED));

        verifyNoInteractions(inventoryClient, compensationService);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrderStatus_whenReleaseFailsAndCompensationCannotBeRecorded_keepsOrderPending() {
        UUID reservationId = UUID.randomUUID();
        Order order = orderWithReservation(OrderStatus.PENDING, reservationId);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        RuntimeException releaseFailure = new RuntimeException("Inventory unavailable");
        RuntimeException persistenceFailure = new RuntimeException("Cannot record retry");
        doThrow(releaseFailure).when(inventoryClient)
                .releaseInventory(reservationId, 3L, 1);
        doThrow(persistenceFailure).when(compensationService)
                .recordFailedRelease(reservationId, 3L, 1, releaseFailure);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> orderService.updateOrderStatus(10L, OrderStatus.CANCELLED));

        assertSame(releaseFailure, thrown);
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertTrue(List.of(thrown.getSuppressed()).contains(persistenceFailure));
        verify(orderRepository, never()).save(any());
    }

    private Order orderWithReservation(OrderStatus status, UUID reservationId) {
        Order order = new Order();
        order.setId(10L);
        order.setStatus(status);

        OrderItem item = new OrderItem();
        item.setReservationId(reservationId);
        item.setProductId(3L);
        item.setQuantity(1);
        order.addItem(item);
        return order;
    }

}
