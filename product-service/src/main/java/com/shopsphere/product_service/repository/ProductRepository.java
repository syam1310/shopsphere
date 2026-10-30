package com.shopsphere.product_service.repository;

import com.shopsphere.product_service.enity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query("""
            select p from Product p
            where p.active = true
              and (:category = '' or p.category = :category)
              and (:search = '' or lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(coalesce(p.description, '')) like lower(concat('%', :search, '%')))
            """)
    Page<Product> searchActive(@Param("category") String category,
                               @Param("search") String search,
                               Pageable pageable);

    List<String> findDistinctCategoryByActiveTrueOrderByCategory();
}
