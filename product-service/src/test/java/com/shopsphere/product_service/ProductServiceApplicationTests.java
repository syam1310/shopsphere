package com.shopsphere.product_service;

import com.shopsphere.product_service.dto.CreateProductRequest;
import com.shopsphere.product_service.dto.ProductResponse;
import com.shopsphere.product_service.dto.UpdateProductRequest;
import com.shopsphere.product_service.enity.Product;
import com.shopsphere.product_service.exception.ProductNotFoundException;
import com.shopsphere.product_service.repository.ProductRepository;
import com.shopsphere.product_service.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceApplicationTests {
    @Mock private ProductRepository productRepository;
    @InjectMocks private ProductService productService;

    @Test void shouldCreateProduct() {
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0); p.setId(1L); return p;
        });
        ProductResponse result = productService.createProduct(new CreateProductRequest(
                "MacBook Pro", "Apple laptop", new BigDecimal("1999.99"), 10, "Electronics", "https://example.com/macbook.jpg"));
        assertEquals("MacBook Pro", result.name());
        assertEquals(new BigDecimal("1999.99"), result.price());
        assertEquals(10, result.quantity());
        assertEquals("Electronics", result.category());
    }

    @Test void shouldGetProductById() {
        Product p = product("MacBook Pro", new BigDecimal("1999.99"), 10, "Electronics");
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));
        ProductResponse result = productService.getProductById(1L);
        assertEquals("MacBook Pro", result.name());
        assertEquals(new BigDecimal("1999.99"), result.price());
        assertEquals(10, result.quantity());
    }

    @Test void shouldThrowExceptionWhenProductDoesNotExist() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());
        ProductNotFoundException exception = assertThrows(ProductNotFoundException.class, () -> productService.getProductById(999L));
        assertEquals("Product not found with id: 999", exception.getMessage());
    }

    @Test void shouldUpdateProduct() {
        Product existing = product("Old Laptop", new BigDecimal("1000.00"), 5, "Electronics");
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProductResponse result = productService.updateProduct(1L, new UpdateProductRequest(
                "New Laptop", "Updated", new BigDecimal("1500.00"), 10, "Computers", null, true));
        assertEquals("New Laptop", result.name());
        assertEquals(new BigDecimal("1500.00"), result.price());
        assertEquals(10, result.quantity());
        assertEquals("Computers", result.category());
    }

    @Test void shouldDeleteProduct() {
        Product existing = product("Laptop", new BigDecimal("1000.00"), 5, "Electronics");
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        productService.deleteProduct(1L);
        verify(productRepository).delete(existing);
    }

    private Product product(String name, BigDecimal price, int quantity, String category) {
        Product p = new Product(); p.setId(1L); p.setName(name); p.setDescription("Test product");
        p.setPrice(price); p.setQuantity(quantity); p.setCategory(category); p.setActive(true); return p;
    }
}
