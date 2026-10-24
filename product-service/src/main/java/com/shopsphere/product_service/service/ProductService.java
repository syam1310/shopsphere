package com.shopsphere.product_service.service;

import com.shopsphere.product_service.dto.CreateProductRequest;
import com.shopsphere.product_service.dto.ProductResponse;
import com.shopsphere.product_service.dto.UpdateProductRequest;
import com.shopsphere.product_service.enity.Product;
import com.shopsphere.product_service.exception.ProductNotFoundException;
import com.shopsphere.product_service.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private static final Logger log =
            LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductResponse createProduct(CreateProductRequest request) {
        log.info("Creating product: {}", request.name());

        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setQuantity(request.quantity());
        product.setCategory(request.category());
        product.setImageUrl(request.imageUrl());
        product.setActive(true);

        return toResponse(productRepository.save(product));
    }

    public List<ProductResponse> getAllProducts() {
        log.info("Fetching all products");

        return productRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public ProductResponse getProductById(Long id) {
        log.info("Fetching product with id: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Product not found with id: {}", id);
                    return new ProductNotFoundException(
                            "Product not found with id: " + id
                    );
                });

        return toResponse(product);
    }

    public ProductResponse updateProduct(
            Long id,
            UpdateProductRequest request) {

        log.info("Updating product with id: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with id: " + id
                ));

        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setQuantity(request.quantity());
        product.setCategory(request.category());
        product.setImageUrl(request.imageUrl());

        if (request.active() != null) {
            product.setActive(request.active());
        }

        return toResponse(productRepository.save(product));
    }

    public void deleteProduct(Long id) {
        log.info("Deleting product with id: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with id: " + id
                ));

        productRepository.delete(product);
        log.info("Product deleted successfully with id: {}", id);
    }

    private ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getQuantity(),
                product.getCategory(),
                product.getImageUrl(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
