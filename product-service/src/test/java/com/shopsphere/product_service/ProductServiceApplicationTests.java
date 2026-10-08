package com.shopsphere.product_service;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceApplicationTests {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldCreateProduct() {

        Product product = new Product();
        product.setName("MacBook Pro");
        product.setDescription("Apple laptop");
        product.setPrice(new BigDecimal("1999.99"));
        product.setQuantity(10);
        product.setCategory("Electronics");
        product.setImageUrl("https://example.com/macbook.jpg");
        product.setActive(true);

        when(productRepository.save(any(Product.class)))
                .thenReturn(product);

        Product result = productService.createProduct(product);

        assertEquals("MacBook Pro", result.getName());
        assertEquals(new BigDecimal("1999.99"), result.getPrice());
        assertEquals(10, result.getQuantity());
        assertEquals("Electronics", result.getCategory());
    }

    @Test
    void shouldGetProductById() {

        Product product = new Product();
        product.setName("MacBook Pro");
        product.setDescription("Apple laptop");
        product.setPrice(new BigDecimal("1999.99"));
        product.setQuantity(10);
        product.setCategory("Electronics");
        product.setActive(true);

        when(productRepository.findById(1L))
                .thenReturn(java.util.Optional.of(product));

        Product result = productService.getProductById(1L);

        assertEquals("MacBook Pro", result.getName());
        assertEquals(new BigDecimal("1999.99"), result.getPrice());
        assertEquals(10, result.getQuantity());
    }

    @Test
    void shouldThrowExceptionWhenProductDoesNotExist() {

        when(productRepository.findById(999L))
                .thenReturn(java.util.Optional.empty());

        ProductNotFoundException exception =
                assertThrows(
                        ProductNotFoundException.class,
                        () -> productService.getProductById(999L)
                );

        assertEquals(
                "Product not found with id: 999",
                exception.getMessage()
        );
    }

    @Test
    void shouldUpdateProduct() {

        Product existingProduct = new Product();
        existingProduct.setName("Old Laptop");
        existingProduct.setPrice(new BigDecimal("1000.00"));
        existingProduct.setQuantity(5);
        existingProduct.setCategory("Electronics");
        existingProduct.setActive(true);

        Product updatedProduct = new Product();
        updatedProduct.setName("New Laptop");
        updatedProduct.setPrice(new BigDecimal("1500.00"));
        updatedProduct.setQuantity(10);
        updatedProduct.setCategory("Computers");
        updatedProduct.setActive(true);

        when(productRepository.findById(1L))
                .thenReturn(java.util.Optional.of(existingProduct));

        when(productRepository.save(any(Product.class)))
                .thenReturn(existingProduct);

        Product result = productService.updateProduct(1L, updatedProduct);

        assertEquals("New Laptop", result.getName());
        assertEquals(new BigDecimal("1500.00"), result.getPrice());
        assertEquals(10, result.getQuantity());
        assertEquals("Computers", result.getCategory());
    }

    @Test
    void shouldDeleteProduct() {

        Product existingProduct = new Product();
        existingProduct.setName("Laptop");
        existingProduct.setPrice(new BigDecimal("1000.00"));
        existingProduct.setQuantity(5);
        existingProduct.setCategory("Electronics");
        existingProduct.setActive(true);

        when(productRepository.findById(1L))
                .thenReturn(java.util.Optional.of(existingProduct));

        productService.deleteProduct(1L);

        org.mockito.Mockito.verify(productRepository)
                .delete(existingProduct);
    }
}