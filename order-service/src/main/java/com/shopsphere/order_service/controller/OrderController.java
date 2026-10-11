package com.shopsphere.order_service.controller;

import com.shopsphere.order_service.dto.CreateOrderRequest;
import com.shopsphere.order_service.entity.Order;
import com.shopsphere.order_service.entity.OrderStatus;
import com.shopsphere.order_service.exception.OrderNotFoundException;
import com.shopsphere.order_service.security.JwtService;
import com.shopsphere.order_service.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final JwtService jwtService;

    public OrderController(OrderService orderService, JwtService jwtService) {
        this.orderService = orderService;
        this.jwtService = jwtService;
    }


    @GetMapping
    public List<Order> getAllOrders(Authentication authentication) {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only administrators can view all orders");
        }

        return orderService.getAllOrders();
    }


    @GetMapping("/{id}")
    public Order getOrderById(
            @PathVariable Long id,
            Authentication authentication) {

        Long authenticatedUserId = (Long) authentication.getPrincipal();

        Order order = orderService.getOrderById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        if (!order.getUserId().equals(authenticatedUserId)
                && !authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN"))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not allowed to access this order"
            );
        }

        return order;
    }


    @GetMapping("/user/{userId}")
    public List<Order> getOrdersByUserId(
            @PathVariable Long userId,
            Authentication authentication) {

        Long authenticatedUserId = (Long) authentication.getPrincipal();

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !authenticatedUserId.equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not allowed to access this user's orders"
            );
        }

        return orderService.getOrdersByUserId(userId);
    }


    @GetMapping("/my")
    public List<Order> getMyOrders(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return orderService.getOrdersByUserId(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Order> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        Long authenticatedUserId = (Long) authentication.getPrincipal();

        String authorizationHeader = httpRequest.getHeader("Authorization");
        String token = authorizationHeader.substring(7);

        Order createdOrder = orderService.createOrder(
                request,
                authenticatedUserId,
                token
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(createdOrder);
    }


    @PatchMapping("/{id}/status")
    public Order updateOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status,
            Authentication authentication) {

        Long authenticatedUserId = (Long) authentication.getPrincipal();

        Order existingOrder = orderService.getOrderById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !existingOrder.getUserId().equals(authenticatedUserId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not allowed to update this order");
        }

        return orderService.updateOrderStatus(id, status);
    }


}
