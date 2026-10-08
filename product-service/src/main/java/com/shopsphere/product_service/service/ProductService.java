package com.shopsphere.product_service.service;

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

    public Product createProduct(Product product) {
        log.info("Creating product: {}", product.getName());
        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        log.info("Fetching all products");
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        log.info("Fetching product with id: {}", id);

        return productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Product not found with id: {}", id);
                    return new ProductNotFoundException(
                            "Product not found with id: " + id
                    );
                });
    }

    public Product updateProduct(Long id, Product updatedProduct) {
        log.info("Updating product with id: {}", id);
        Product existingProduct = getProductById(id);

        existingProduct.setName(updatedProduct.getName());
        existingProduct.setDescription(updatedProduct.getDescription());
        existingProduct.setPrice(updatedProduct.getPrice());
        existingProduct.setQuantity(updatedProduct.getQuantity());
        existingProduct.setCategory(updatedProduct.getCategory());
        existingProduct.setImageUrl(updatedProduct.getImageUrl());
        existingProduct.setActive(updatedProduct.isActive());

        return productRepository.save(existingProduct);
    }

    public void deleteProduct(Long id) {
        log.info("Deleting product with id: {}", id);
        Product existingProduct = getProductById(id);
        productRepository.delete(existingProduct);
        log.info("Product deleted successfully with id: {}", id);
    }
}