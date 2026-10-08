package com.shopsphere.product_service.repository;

import com.shopsphere.product_service.enity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
