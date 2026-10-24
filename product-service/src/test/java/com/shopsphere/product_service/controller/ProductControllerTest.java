package com.shopsphere.product_service.controller;

import com.shopsphere.product_service.dto.CreateProductRequest;
import com.shopsphere.product_service.dto.ProductResponse;
import com.shopsphere.product_service.dto.UpdateProductRequest;
import com.shopsphere.product_service.exception.ProductNotFoundException;
import com.shopsphere.product_service.security.JwtService;
import com.shopsphere.product_service.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private JwtService jwtService;


    @Test
    void shouldReturn400WhenProductValidationFails() throws Exception {

        String invalidProduct = """
                {
                    "name": "",
                    "description": "Invalid product",
                    "price": -100,
                    "quantity": -5,
                    "category": "",
                    "imageUrl": "",
                    "active": true
                }
                """;

        mockMvc.perform(
                        post("/api/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidProduct)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name")
                        .value("Product name is required"))
                .andExpect(jsonPath("$.errors.price")
                        .value("Price must be greater than zero"))
                .andExpect(jsonPath("$.errors.quantity")
                        .value("Quantity cannot be negative"))
                .andExpect(jsonPath("$.errors.category")
                        .value("Category is required"));
    }

    @Test
    void shouldCreateProductSuccessfully() throws Exception {

        String productJson = """
                {
                    "name": "iPhone 17",
                    "description": "Latest Apple smartphone",
                    "price": 79999.99,
                    "quantity": 10,
                    "category": "Electronics",
                    "imageUrl": "https://example.com/iphone17.jpg",
                    "active": true
                }
                """;

        ProductResponse product = new ProductResponse(1L, "iPhone 17", "Latest Apple smartphone",
                new BigDecimal("79999.99"), 10, "Electronics", "https://example.com/iphone17.jpg", true, null, null);
        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(product);

        mockMvc.perform(
                        post("/api/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(productJson)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("iPhone 17"))
                .andExpect(jsonPath("$.price").value(79999.99))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.category").value("Electronics"));
    }

    @Test
    void shouldGetProductByIdSuccessfully() throws Exception {

        ProductResponse product = new ProductResponse(1L, "iPhone 17", "Latest Apple smartphone",
                new BigDecimal("79999.99"), 10, "Electronics", "https://example.com/iphone17.jpg", true, null, null);
        when(productService.getProductById(1L)).thenReturn(product);

        mockMvc.perform(
                        get("/api/products/1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("iPhone 17"))
                .andExpect(jsonPath("$.price").value(79999.99))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.category").value("Electronics"));
    }

    @Test
    void shouldGetAllProductsSuccessfully() throws Exception {

        ProductResponse product1 = new ProductResponse(1L, "iPhone 17", null,
                new BigDecimal("79999.99"), 10, "Electronics", null, true, null, null);
        ProductResponse product2 = new ProductResponse(2L, "MacBook Air", null,
                new BigDecimal("99999.99"), 5, "Laptops", null, true, null, null);

        when(productService.getAllProducts())
                .thenReturn(List.of(product1, product2));

        mockMvc.perform(
                        get("/api/products")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("iPhone 17"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("MacBook Air"));
    }

    @Test
    void shouldUpdateProductSuccessfully() throws Exception {

        String productJson = """
                {
                    "name": "iPhone 17 Pro",
                    "description": "Updated Apple smartphone",
                    "price": 89999.99,
                    "quantity": 15,
                    "category": "Electronics",
                    "imageUrl": "https://example.com/iphone17pro.jpg",
                    "active": true
                }
                """;

        ProductResponse updatedProduct = new ProductResponse(1L, "iPhone 17 Pro", "Updated Apple smartphone",
                new BigDecimal("89999.99"), 15, "Electronics", "https://example.com/iphone17pro.jpg", true, null, null);
        when(productService.updateProduct(eq(1L), any(UpdateProductRequest.class))).thenReturn(updatedProduct);

        mockMvc.perform(
                        put("/api/products/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(productJson)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("iPhone 17 Pro"))
                .andExpect(jsonPath("$.price").value(89999.99))
                .andExpect(jsonPath("$.quantity").value(15))
                .andExpect(jsonPath("$.category").value("Electronics"));
    }

    @Test
    void shouldDeleteProductSuccessfully() throws Exception {

        doNothing().when(productService).deleteProduct(1L);

        mockMvc.perform(
                        delete("/api/products/1")
                )
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(1L);
    }

    @Test
    void shouldReturn404WhenDeletingNonExistingProduct() throws Exception {

        doThrow(new ProductNotFoundException(
                "Product not found with id: 999"
        )).when(productService).deleteProduct(999L);

        mockMvc.perform(
                        delete("/api/products/999")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Product not found with id: 999"));

        verify(productService).deleteProduct(999L);
    }

    @Test
    void shouldReturn404WhenGettingNonExistingProduct() throws Exception {

        when(productService.getProductById(999L))
                .thenThrow(new ProductNotFoundException(
                        "Product not found with id: 999"
                ));

        mockMvc.perform(
                        get("/api/products/999")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Product not found with id: 999"));

        verify(productService).getProductById(999L);
    }

    @Test
    void shouldReturn400WhenUpdatingProductWithInvalidData() throws Exception {

        String invalidProduct = """
                {
                    "name": "",
                    "description": "Invalid update",
                    "price": -100,
                    "quantity": -5,
                    "category": "",
                    "imageUrl": "",
                    "active": true
                }
                """;

        mockMvc.perform(
                        put("/api/products/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidProduct)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name")
                        .value("Product name is required"))
                .andExpect(jsonPath("$.errors.price")
                        .value("Price must be greater than zero"))
                .andExpect(jsonPath("$.errors.quantity")
                        .value("Quantity cannot be negative"))
                .andExpect(jsonPath("$.errors.category")
                        .value("Category is required"));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingProduct() throws Exception {

        String productJson = """
                {
                    "name": "iPhone 17 Pro",
                    "description": "Updated Apple smartphone",
                    "price": 89999.99,
                    "quantity": 15,
                    "category": "Electronics",
                    "imageUrl": "https://example.com/iphone17pro.jpg",
                    "active": true
                }
                """;

        when(productService.updateProduct(
                eq(999L),
                any(UpdateProductRequest.class)
        )).thenThrow(new ProductNotFoundException(
                "Product not found with id: 999"
        ));

        mockMvc.perform(
                        put("/api/products/999")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(productJson)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Product not found with id: 999"));

        verify(productService).updateProduct(
                eq(999L),
                any(UpdateProductRequest.class)
        );
    }


}