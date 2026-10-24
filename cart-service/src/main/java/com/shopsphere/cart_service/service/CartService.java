
package com.shopsphere.cart_service.service;

import com.shopsphere.cart_service.client.ProductClient;
import com.shopsphere.cart_service.dto.AddCartItemRequest;
import com.shopsphere.cart_service.dto.CartItemResponse;
import com.shopsphere.cart_service.dto.CartResponse;
import com.shopsphere.cart_service.dto.ProductResponse;
import com.shopsphere.cart_service.dto.UpdateCartItemRequest;
import com.shopsphere.cart_service.entity.Cart;
import com.shopsphere.cart_service.entity.CartItem;
import com.shopsphere.cart_service.repository.CartItemRepository;
import com.shopsphere.cart_service.repository.CartRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductClient productClient;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductClient productClient) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productClient = productClient;
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElse(null);

        if (cart == null) {
            return new CartResponse(
                    null,
                    userId,
                    List.of(),
                    BigDecimal.ZERO,
                    null,
                    null
            );
        }

        return toResponse(cart);
    }

    @Transactional
    public CartResponse addItem(
            Long userId,
            AddCartItemRequest request,
            String authorization) {

        ProductResponse product =
                getActiveProduct(request.productId(), authorization);

        Cart cart = getOrCreateCart(userId);

        CartItem item = cartItemRepository
                .findByCartIdAndProductId(
                        cart.getId(),
                        product.id()
                )
                .orElse(null);

        int newQuantity = request.quantity();

        if (item == null) {
            item = new CartItem();
            item.setProductId(product.id());
            item.setProductName(product.name());
            item.setUnitPrice(product.price());
            item.setQuantity(newQuantity);
            cart.addItem(item);
        } else {
            item.setQuantity(
                    Math.addExact(item.getQuantity(), newQuantity)
            );
            item.setProductName(product.name());
            item.setUnitPrice(product.price());
        }

        cartRepository.save(cart);

        return toResponse(
                cartRepository.findById(cart.getId()).orElseThrow()
        );
    }

    @Transactional
    public CartResponse updateItem(
            Long userId,
            Long productId,
            UpdateCartItemRequest request,
            String authorization) {

        Cart cart = getExistingCart(userId);
        ProductResponse product = getActiveProduct(productId, authorization);

        CartItem item = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product is not in the cart"
                ));

        item.setQuantity(request.quantity());
        item.setProductName(product.name());
        item.setUnitPrice(product.price());

        cartRepository.save(cart);

        return toResponse(
                cartRepository.findById(cart.getId()).orElseThrow()
        );
    }

    @Transactional
    public void removeItem(Long userId, Long productId) {
        Cart cart = getExistingCart(userId);

        CartItem item = cartItemRepository
                .findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product is not in the cart"
                ));

        cart.removeItem(item);
        cartRepository.save(cart);
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getExistingCart(userId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUserId(userId);
                    return cartRepository.save(cart);
                });
    }

    private Cart getExistingCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Cart not found"
                ));
    }


    private ProductResponse getActiveProduct(
            Long productId,
            String authorization) {

        ProductResponse product;

        try {
            product = productClient.getProductById(productId, authorization);

        } catch (RestClientResponseException ex) {

            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Product not found: " + productId,
                        ex
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Product Service request failed with status: "
                            + ex.getStatusCode().value(),
                    ex
            );

        } catch (ResponseStatusException ex) {
            throw ex;

        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to retrieve product details",
                    ex
            );
        }

        if (product == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Product Service returned an empty response"
            );
        }

        if (!product.active()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Inactive products cannot be added to the cart"
            );
        }

        if (product.price() == null
                || product.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Product Service returned an invalid price"
            );
        }

        return product;
    }


    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems()
                .stream()
                .map(item -> new CartItemResponse(
                        item.getProductId(),
                        item.getProductName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getUnitPrice().multiply(
                                BigDecimal.valueOf(item.getQuantity())
                        )
                ))
                .toList();

        BigDecimal total = items.stream()
                .map(CartItemResponse::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(
                cart.getId(),
                cart.getUserId(),
                items,
                total,
                cart.getCreatedAt(),
                cart.getUpdatedAt()
        );
    }
}
