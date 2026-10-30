package com.shopsphere.order_service.controller;

import com.shopsphere.order_service.client.CartClient;
import com.shopsphere.order_service.client.NotificationClient;
import com.shopsphere.order_service.client.PaymentClient;
import com.shopsphere.order_service.client.UserClient;
import com.shopsphere.order_service.dto.CreateOrderRequest;
import com.shopsphere.order_service.dto.BuyNowRequest;
import com.shopsphere.order_service.dto.OrderItemRequest;
import com.shopsphere.order_service.entity.CheckoutRequest;
import com.shopsphere.order_service.entity.CheckoutStatus;
import com.shopsphere.order_service.entity.Order;
import com.shopsphere.order_service.entity.OrderStatus;
import com.shopsphere.order_service.exception.OrderNotFoundException;
import com.shopsphere.order_service.security.JwtService;
import com.shopsphere.order_service.service.CheckoutIdempotencyService;
import com.shopsphere.order_service.service.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log =
            LoggerFactory.getLogger(OrderController.class);
    private final OrderService orderService;
    private final JwtService jwtService;
    private final CartClient cartClient;
    private final CheckoutIdempotencyService checkoutIdempotencyService;
    private final PaymentClient paymentClient;
    private final UserClient userClient;
    private final NotificationClient notificationClient;


    public OrderController(
            OrderService orderService,
            JwtService jwtService,
            CartClient cartClient,
            PaymentClient paymentClient,
            CheckoutIdempotencyService checkoutIdempotencyService,
            UserClient userClient,
            NotificationClient notificationClient) {
        this.orderService = orderService;
        this.jwtService = jwtService;
        this.cartClient = cartClient;
        this.paymentClient = paymentClient;
        this.checkoutIdempotencyService = checkoutIdempotencyService;
        this.userClient = userClient;
        this.notificationClient = notificationClient;
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

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN"));

        if (!order.getUserId().equals(authenticatedUserId) && !isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not allowed to access this order");
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
                    "You are not allowed to access this user's orders");
        }

        return orderService.getOrdersByUserId(userId);
    }

    @GetMapping("/my")
    public List<Order> getMyOrders(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return orderService.getOrdersByUserId(userId);
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        Long authenticatedUserId = (Long) authentication.getPrincipal();

        String authorizationHeader = httpRequest.getHeader("Authorization");

        if (authorizationHeader == null ||
                !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing or invalid bearer token");
        }

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

        // Customers may cancel their own orders only.
        if (!isAdmin && status != OrderStatus.CANCELLED) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Customers may only cancel their own orders");
        }

        if (status == OrderStatus.CANCELLED
                && existingOrder.getStatus() == OrderStatus.PENDING
                && existingOrder.getIdempotencyKey() != null) {
            Optional<CheckoutRequest> checkout = checkoutIdempotencyService.find(
                    existingOrder.getUserId(), existingOrder.getIdempotencyKey());
            if (checkout.isPresent()
                    && checkout.get().getStatus() == CheckoutStatus.PROCESSING) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Checkout is still processing; retry cancellation after it completes");
            }
        }

        return orderService.updateOrderStatus(id, status);
    }

    @PostMapping("/checkout")
    public ResponseEntity<Order> checkout(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam(defaultValue = "UPI") String paymentMethod,
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        if (idempotencyKey == null ||
                idempotencyKey.isBlank() ||
                idempotencyKey.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain 1 to 100 characters");
        }

        if (paymentMethod == null ||
                !java.util.Set.of("UPI", "CARD", "WALLET")
                        .contains(paymentMethod.trim().toUpperCase(java.util.Locale.ROOT))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "paymentMethod must be UPI, CARD, or WALLET");
        }

        paymentMethod = paymentMethod.trim().toUpperCase(java.util.Locale.ROOT);

        Long userId = (Long) authentication.getPrincipal();
        String authorizationHeader = httpRequest.getHeader("Authorization");

        if (authorizationHeader == null ||
                !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Missing or invalid bearer token");
        }

        // Atomically claim this user's idempotency key.
        boolean claimed = checkoutIdempotencyService.claim(userId, idempotencyKey);

        if (!claimed) {

            // Recover an order if it was persisted before checkout status updated.
            Optional<Order> persistedOrder =
                    orderService.findByUserIdAndIdempotencyKey(
                            userId, idempotencyKey);

            if (persistedOrder.isPresent()) {
                Order existingOrder = persistedOrder.get();

                if (existingOrder.getStatus() == OrderStatus.CONFIRMED) {
                    checkoutIdempotencyService.markCompleted(
                            userId, idempotencyKey, existingOrder.getId());
                    enqueueOrderConfirmation(existingOrder, userId, authorizationHeader);

                    cartClient.clearCart(authorizationHeader);

                    return ResponseEntity.ok(existingOrder);
                }

                if (existingOrder.getStatus() == OrderStatus.CANCELLED) {
                    checkoutIdempotencyService.markFailed(userId, idempotencyKey);

                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "This order was cancelled; use a new checkout key");
                }

                Order reconciledOrder = reconcilePendingOrder(
                        existingOrder,
                        userId,
                        idempotencyKey,
                        paymentMethod,
                        authorizationHeader);

                if (reconciledOrder.getStatus() == OrderStatus.CONFIRMED) {
                    return ResponseEntity.ok(reconciledOrder);
                }

                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Checkout is still pending reconciliation; retry with the same key");
            }

            CheckoutRequest existing = checkoutIdempotencyService
                    .find(userId, idempotencyKey)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Checkout request is being processed; retry with the same key"));

            if (existing.getStatus() == CheckoutStatus.COMPLETED
                    && existing.getOrderId() != null) {

                Order existingOrder = orderService
                        .getOrderById(existing.getOrderId())
                        .orElseThrow(() ->
                                new OrderNotFoundException(existing.getOrderId()));

                if (existingOrder.getStatus() != OrderStatus.CONFIRMED) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Checkout is marked completed but the order is not confirmed");
                }

                enqueueOrderConfirmation(existingOrder, userId, authorizationHeader);
                cartClient.clearCart(authorizationHeader);

                return ResponseEntity.ok(existingOrder);
            }

            if (existing.getStatus() == CheckoutStatus.PROCESSING) {
                return waitForCheckoutCompletion(
                        userId,
                        idempotencyKey,
                        authorizationHeader);
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This checkout key belongs to a failed request; use a new key");
        }

        try {
            CartClient.CartResponse cart = cartClient.getCart(authorizationHeader);

            if (cart == null || cart.items() == null || cart.items().isEmpty()) {
                throw new IllegalArgumentException("Cannot checkout with an empty cart");
            }

            if (cart.userId() == null || !cart.userId().equals(userId)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Cart does not belong to the authenticated user");
            }

            var orderItems = cart.items().stream()
                    .map(item -> new OrderItemRequest(item.productId(), item.quantity()))
                    .collect(Collectors.toList());

            CreateOrderRequest request = new CreateOrderRequest(orderItems);
            String token = authorizationHeader.substring(7);

            Order createdOrder = orderService.createOrder(
                    request, userId, token, idempotencyKey);

            PaymentClient.PaymentResponse payment = paymentClient.createPayment(
                    new PaymentClient.CreatePaymentRequest(
                            createdOrder.getId(),
                            userId,
                            createdOrder.getTotalAmount(),
                            paymentMethod),
                    idempotencyKey);

            if (payment == null || payment.status() == null) {
                throw new PaymentClient.PaymentServiceException(
                        "Payment Service returned an empty response",
                        new IllegalStateException("Missing payment response"));
            }

            if ("FAILED".equalsIgnoreCase(payment.status())) {
                orderService.updateOrderStatus(createdOrder.getId(), OrderStatus.CANCELLED);
                checkoutIdempotencyService.markFailed(userId, idempotencyKey);
                throw new ResponseStatusException(
                        HttpStatus.PAYMENT_REQUIRED,
                        "Payment failed; the order was cancelled");
            }

            if (!"SUCCESS".equalsIgnoreCase(payment.status())) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Payment status is not final; checkout requires reconciliation");
            }

            Order confirmedOrder = orderService.updateOrderStatus(
                    createdOrder.getId(), OrderStatus.CONFIRMED);

            // Persist checkout completion before attempting external side effects.
            checkoutIdempotencyService.markCompleted(
                    userId, idempotencyKey, confirmedOrder.getId());

            // Notification submission is best-effort; the queue sends email asynchronously.
            enqueueOrderConfirmation(confirmedOrder, userId, authorizationHeader);
            cartClient.clearCart(authorizationHeader);

            return ResponseEntity.status(HttpStatus.CREATED).body(confirmedOrder);

        } catch (RuntimeException exception) {
            Optional<Order> persistedOrder =
                    orderService.findByUserIdAndIdempotencyKey(userId, idempotencyKey);

            try {
                if (persistedOrder.isPresent()) {
                    Order existingOrder = persistedOrder.get();

                    if (existingOrder.getStatus() == OrderStatus.CONFIRMED) {
                        checkoutIdempotencyService.markCompleted(
                                userId, idempotencyKey, existingOrder.getId());
                        enqueueOrderConfirmation(
                                existingOrder, userId, authorizationHeader);
                        // Do not let a cart-clear failure turn a paid order into a failed checkout.
                        if (!(exception instanceof ResponseStatusException)) {
                            cartClient.clearCart(authorizationHeader);
                            return ResponseEntity.ok(existingOrder);
                        }
                    } else if (existingOrder.getStatus() == OrderStatus.CANCELLED) {
                        checkoutIdempotencyService.markFailed(userId, idempotencyKey);
                    } else if (existingOrder.getStatus() == OrderStatus.PENDING
                            && exception instanceof PaymentClient.PaymentServiceException) {
                        Order reconciledOrder = reconcilePendingOrder(
                                existingOrder,
                                userId,
                                idempotencyKey,
                                paymentMethod,
                                authorizationHeader);
                        if (reconciledOrder.getStatus() == OrderStatus.CONFIRMED) {
                            return ResponseEntity.ok(reconciledOrder);
                        }
                    }
                    // Leave PENDING orders recoverable for payment reconciliation.
                } else {
                    CheckoutRequest current = checkoutIdempotencyService
                            .find(userId, idempotencyKey)
                            .orElse(null);
                    if (current != null && current.getStatus() != CheckoutStatus.COMPLETED) {
                        checkoutIdempotencyService.markFailed(userId, idempotencyKey);
                    }
                }
            } catch (ResponseStatusException recoveryException) {
                throw recoveryException;
            } catch (RuntimeException recoveryException) {
                exception.addSuppressed(recoveryException);
            }

            if (exception instanceof HttpClientErrorException.Conflict) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Inventory is insufficient for one or more items. Remove an unavailable item and try again.",
                        exception);
            }

            throw exception;
        }
    }

    @PostMapping("/checkout/buy-now")
    public ResponseEntity<Order> buyNow(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam(defaultValue = "UPI") String paymentMethod,
            @Valid @RequestBody BuyNowRequest request,
            Authentication authentication,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain 1 to 100 characters");
        }
        paymentMethod = paymentMethod.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("UPI", "CARD", "WALLET").contains(paymentMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "paymentMethod must be UPI, CARD, or WALLET");
        }
        Long userId = (Long) authentication.getPrincipal();
        String authorizationHeader = httpRequest.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid bearer token");
        }
        String token = authorizationHeader.substring(7);

        if (!checkoutIdempotencyService.claim(userId, idempotencyKey)) {
            Optional<Order> existing = orderService.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                Order order = existing.get();
                if (order.getStatus() == OrderStatus.CONFIRMED) return ResponseEntity.ok(order);
                if (order.getStatus() == OrderStatus.PENDING) {
                    Order recovered = reconcilePendingOrder(order, userId, idempotencyKey,
                            paymentMethod, authorizationHeader, false);
                    return ResponseEntity.ok(recovered);
                }
                checkoutIdempotencyService.markFailed(userId, idempotencyKey);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This order was cancelled; use a new checkout key");
            }
            CheckoutRequest current = checkoutIdempotencyService.find(userId, idempotencyKey)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "Checkout request is being processed; retry with the same key"));
            if (current.getStatus() == CheckoutStatus.COMPLETED && current.getOrderId() != null) {
                return ResponseEntity.ok(orderService.getOrderById(current.getOrderId())
                        .orElseThrow(() -> new OrderNotFoundException(current.getOrderId())));
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    current.getStatus() == CheckoutStatus.FAILED
                            ? "This checkout key belongs to a failed request; use a new key"
                            : "Checkout is still processing; retry with the same key");
        }

        try {
            CreateOrderRequest orderRequest = new CreateOrderRequest(List.of(
                    new OrderItemRequest(request.productId(), request.quantity())));
            Order created = orderService.createOrder(orderRequest, userId, token, idempotencyKey);
            PaymentClient.PaymentResponse payment = paymentClient.createPayment(
                    new PaymentClient.CreatePaymentRequest(created.getId(), userId,
                            created.getTotalAmount(), paymentMethod), idempotencyKey);
            if (payment == null || payment.status() == null) {
                throw new PaymentClient.PaymentServiceException("Payment Service returned an empty response",
                        new IllegalStateException("Missing payment response"));
            }
            if ("FAILED".equalsIgnoreCase(payment.status())) {
                orderService.updateOrderStatus(created.getId(), OrderStatus.CANCELLED);
                checkoutIdempotencyService.markFailed(userId, idempotencyKey);
                throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                        "Payment failed; the order was cancelled");
            }
            if (!"SUCCESS".equalsIgnoreCase(payment.status())) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Payment status is not final; retry with the same key");
            }
            Order confirmed = orderService.updateOrderStatus(created.getId(), OrderStatus.CONFIRMED);
            checkoutIdempotencyService.markCompleted(userId, idempotencyKey, confirmed.getId());
            enqueueOrderConfirmation(confirmed, userId, authorizationHeader);
            return ResponseEntity.status(HttpStatus.CREATED).body(confirmed);
        } catch (RuntimeException exception) {
            Optional<Order> persisted = orderService.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (persisted.isPresent() && persisted.get().getStatus() == OrderStatus.PENDING
                    && exception instanceof PaymentClient.PaymentServiceException) {
                return ResponseEntity.ok(reconcilePendingOrder(persisted.get(), userId,
                        idempotencyKey, paymentMethod, authorizationHeader, false));
            }
            if (persisted.isEmpty() || persisted.get().getStatus() == OrderStatus.CANCELLED) {
                checkoutIdempotencyService.markFailed(userId, idempotencyKey);
            }
            if (exception instanceof HttpClientErrorException.Conflict) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Inventory is insufficient for this item", exception);
            }
            throw exception;
        }
    }

    private ResponseEntity<Order> waitForCheckoutCompletion(
            Long userId,
            String idempotencyKey,
            String authorizationHeader) {

        long timeoutNanos = java.util.concurrent.TimeUnit.SECONDS
                .toNanos(2);
        long deadline = System.nanoTime() + timeoutNanos;

        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Checkout is still processing; retry with the same key");
            }

            // Recover the order if it was saved before the status was updated.
            Optional<Order> persistedOrder =
                    orderService.findByUserIdAndIdempotencyKey(
                            userId, idempotencyKey);

            if (persistedOrder.isPresent()) {
                Order order = persistedOrder.get();

                if (order.getStatus() == OrderStatus.CONFIRMED) {
                    checkoutIdempotencyService.markCompleted(
                            userId, idempotencyKey, order.getId());
                    enqueueOrderConfirmation(order, userId, authorizationHeader);

                    cartClient.clearCart(authorizationHeader);

                    return ResponseEntity.ok(order);
                }

                if (order.getStatus() == OrderStatus.CANCELLED) {
                    checkoutIdempotencyService.markFailed(userId, idempotencyKey);

                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "This order was cancelled; use a new checkout key");
                }
            }

            // Check whether the checkout status has changed.
            Optional<CheckoutRequest> checkoutRequest =
                    checkoutIdempotencyService.find(
                            userId, idempotencyKey);

            if (checkoutRequest.isEmpty()) {
                continue;
            }

            CheckoutRequest current = checkoutRequest.get();

            if (current.getStatus() == CheckoutStatus.COMPLETED
                    && current.getOrderId() != null) {

                Order order = orderService
                        .getOrderById(current.getOrderId())
                        .orElseThrow(() ->
                                new OrderNotFoundException(
                                        current.getOrderId()));

                if (order.getStatus() != OrderStatus.CONFIRMED) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Checkout is marked completed but the order is not confirmed");
                }

                enqueueOrderConfirmation(order, userId, authorizationHeader);
                cartClient.clearCart(authorizationHeader);

                return ResponseEntity.ok(order);
            }

            if (current.getStatus() == CheckoutStatus.FAILED) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This checkout key belongs to a failed request; use a new key");
            }
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Checkout is still processing; retry with the same key");
    }

    private Order reconcilePendingOrder(
            Order order,
            Long userId,
            String idempotencyKey,
            String paymentMethod,
            String authorizationHeader) {
        return reconcilePendingOrder(order, userId, idempotencyKey, paymentMethod, authorizationHeader, true);
    }

    private Order reconcilePendingOrder(
            Order order,
            Long userId,
            String idempotencyKey,
            String paymentMethod,
            String authorizationHeader,
            boolean clearCart) {

        if (order.getStatus() != OrderStatus.PENDING) {
            return order;
        }

        final PaymentClient.PaymentResponse payment;

        try {
            // Creating a payment is idempotent by checkout key. This both
            // recovers a payment whose response was lost and creates one when
            // the first attempt failed before Payment Service persisted it.
            payment = paymentClient.createPayment(
                    new PaymentClient.CreatePaymentRequest(
                            order.getId(),
                            userId,
                            order.getTotalAmount(),
                            paymentMethod),
                    idempotencyKey);
        } catch (PaymentClient.PaymentServiceException exception) {
            log.warn(
                    "Could not resume simulated payment for pending order {}. "
                            + "Retry checkout with the same key after the service recovers.",
                    order.getId(),
                    exception);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment could not be resumed; retry with the same checkout key",
                    exception);
        }

        if (payment == null
                || payment.id() == null
                || payment.orderId() == null
                || payment.userId() == null
                || payment.amount() == null
                || payment.status() == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Payment response is incomplete; checkout requires reconciliation");
        }

        boolean paymentMatchesOrder =
                payment.orderId().equals(order.getId())
                        && payment.userId().equals(userId)
                        && payment.amount().compareTo(order.getTotalAmount()) == 0;

        if (!paymentMatchesOrder) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment details do not match the pending order");
        }

        if ("SUCCESS".equalsIgnoreCase(payment.status())) {
            Order confirmedOrder = orderService.updateOrderStatus(
                    order.getId(), OrderStatus.CONFIRMED);

            checkoutIdempotencyService.markCompleted(
                    userId, idempotencyKey, confirmedOrder.getId());

            enqueueOrderConfirmation(
                    confirmedOrder, userId, authorizationHeader);

            if (clearCart) cartClient.clearCart(authorizationHeader);

            return confirmedOrder;
        }

        if ("FAILED".equalsIgnoreCase(payment.status())) {
            orderService.updateOrderStatus(order.getId(), OrderStatus.CANCELLED);
            checkoutIdempotencyService.markFailed(userId, idempotencyKey);

            throw new ResponseStatusException(
                    HttpStatus.PAYMENT_REQUIRED,
                    "Payment failed; the order was cancelled");
        }

        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Payment is not in a final successful or failed state; retry with the same checkout key");
    }

    private void enqueueOrderConfirmation(
            Order order,
            Long expectedUserId,
            String authorizationHeader) {

        if (order == null
                || order.getId() == null
                || order.getUserId() == null
                || order.getTotalAmount() == null
                || order.getStatus() != OrderStatus.CONFIRMED) {
            return;
        }

        if (!order.getUserId().equals(expectedUserId)) {
            log.error(
                    "Skipping order confirmation notification due to user mismatch. "
                            + "Order ID: {}",
                    order.getId());
            return;
        }

        try {
            UserClient.UserResponse user =
                    userClient.getCurrentUser(authorizationHeader);

            if (user == null
                    || user.id() == null
                    || !user.id().equals(expectedUserId)
                    || user.email() == null
                    || user.email().isBlank()) {
                log.error(
                        "Skipping order confirmation notification because the "
                                + "authenticated user profile is invalid. Order ID: {}",
                        order.getId());
                return;
            }

            notificationClient.enqueueOrderConfirmation(
                    order.getId(),
                    user.id(),
                    user.email(),
                    order.getTotalAmount());

            log.info(
                    "Order confirmation email job accepted for order ID {}",
                    order.getId());

        } catch (Exception exception) {
            // Do not turn an already-confirmed, paid order into a checkout failure.
            log.error(
                    "Could not enqueue confirmation email for order ID {}. "
                            + "A retry or durable outbox is needed.",
                    order.getId(),
                    exception);
        }
    }
}
