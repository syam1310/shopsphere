package com.shopsphere.product_service.controller;

import com.shopsphere.product_service.enity.Product;
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

        Product product = new Product();
        product.setId(1L);
        product.setName("iPhone 17");
        product.setDescription("Latest Apple smartphone");
        product.setPrice(new BigDecimal("79999.99"));
        product.setQuantity(10);
        product.setCategory("Electronics");
        product.setImageUrl("https://example.com/iphone17.jpg");
        product.setActive(true);

        when(productService.createProduct(any(Product.class)))
                .thenReturn(product);

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

        Product product = new Product();
        product.setId(1L);
        product.setName("iPhone 17");
        product.setDescription("Latest Apple smartphone");
        product.setPrice(new BigDecimal("79999.99"));
        product.setQuantity(10);
        product.setCategory("Electronics");
        product.setImageUrl("https://example.com/iphone17.jpg");
        product.setActive(true);

        when(productService.getProductById(1L))
                .thenReturn(product);

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

        Product product1 = new Product();
        product1.setId(1L);
        product1.setName("iPhone 17");
        product1.setPrice(new BigDecimal("79999.99"));
        product1.setQuantity(10);
        product1.setCategory("Electronics");
        product1.setActive(true);

        Product product2 = new Product();
        product2.setId(2L);
        product2.setName("MacBook Air");
        product2.setPrice(new BigDecimal("99999.99"));
        product2.setQuantity(5);
        product2.setCategory("Laptops");
        product2.setActive(true);

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

        Product updatedProduct = new Product();
        updatedProduct.setId(1L);
        updatedProduct.setName("iPhone 17 Pro");
        updatedProduct.setDescription("Updated Apple smartphone");
        updatedProduct.setPrice(new BigDecimal("89999.99"));
        updatedProduct.setQuantity(15);
        updatedProduct.setCategory("Electronics");
        updatedProduct.setImageUrl("https://example.com/iphone17pro.jpg");
        updatedProduct.setActive(true);

        when(productService.updateProduct(
                eq(1L),
                any(Product.class)
        )).thenReturn(updatedProduct);

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
                any(Product.class)
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
                any(Product.class)
        );
    }


}