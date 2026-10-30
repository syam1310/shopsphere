package com.shopsphere.product_service.controller;

import com.shopsphere.product_service.dto.CreateProductRequest;
import com.shopsphere.product_service.dto.ProductResponse;
import com.shopsphere.product_service.dto.ProductCatalogPage;
import com.shopsphere.product_service.dto.UpdateProductRequest;
import com.shopsphere.product_service.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        ProductResponse createdProduct =
                productService.createProduct(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createdProduct);
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/catalog")
    public ResponseEntity<ProductCatalogPage> getCatalogPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "48") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be between 1 and 100");
        }
        return ResponseEntity.ok(ProductCatalogPage.from(
                productService.getProducts(page, size, category, search)));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(productService.getCategories());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(
            @PathVariable Long id) {

        return ResponseEntity.ok(productService.getProductById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {

        return ResponseEntity.ok(
                productService.updateProduct(id, request)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
