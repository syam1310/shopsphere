package com.shopsphere.product_service.service;

import com.shopsphere.product_service.dto.CreateProductRequest;
import com.shopsphere.product_service.client.InventoryClient;
import com.shopsphere.product_service.dto.InventoryAvailability;
import com.shopsphere.product_service.dto.ProductResponse;
import com.shopsphere.product_service.dto.UpdateProductRequest;
import com.shopsphere.product_service.enity.Product;
import com.shopsphere.product_service.exception.ProductNotFoundException;
import com.shopsphere.product_service.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final Logger log =
            LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final InventoryClient inventoryClient;

    @Autowired
    public ProductService(ProductRepository productRepository, InventoryClient inventoryClient) {
        this.productRepository = productRepository;
        this.inventoryClient = inventoryClient;
    }

    // Kept for focused unit tests that do not start the inventory service.
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
        this.inventoryClient = null;
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

        Product saved = productRepository.save(product);
        return toResponse(saved, availability(List.of(saved.getId())));
    }

    public List<ProductResponse> getAllProducts() {
        log.info("Fetching all products");

        List<Product> rows = productRepository.findAll(PageRequest.of(0, 48, Sort.by("id"))).getContent();
        Map<Long, Integer> available = availability(rows.stream().map(Product::getId).toList());
        return rows.stream()
                .map(product -> toResponse(product, available))
                .toList();
    }

    public Page<ProductResponse> getProducts(int page, int size, String category, String search) {
        Page<Product> rows = productRepository.searchActive(
                blankToEmpty(category), blankToEmpty(search),
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")));
        Map<Long, Integer> available = availability(rows.getContent().stream()
                .map(Product::getId).toList());
        return rows.map(product -> toResponse(product, available));
    }

    public List<String> getCategories() {
        return productRepository.findDistinctCategoryByActiveTrueOrderByCategory();
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

        return toResponse(product, availability(List.of(product.getId())));
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

        Product saved = productRepository.save(product);
        return toResponse(saved, availability(List.of(saved.getId())));
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

    private Map<Long, Integer> availability(List<Long> productIds) {
        if (inventoryClient == null || productIds.isEmpty()) {
            return null;
        }

        return inventoryClient.getAvailability(productIds).stream()
                .collect(Collectors.toMap(
                        InventoryAvailability::productId,
                        InventoryAvailability::availableQuantity,
                        Math::min));
    }

    private String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.trim();
    }

    private ProductResponse toResponse(Product product, Map<Long, Integer> availability) {
        int availableQuantity = availability == null
                ? product.getQuantity()
                : availability.getOrDefault(product.getId(), 0);
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                availableQuantity,
                product.getCategory(),
                product.getImageUrl(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
