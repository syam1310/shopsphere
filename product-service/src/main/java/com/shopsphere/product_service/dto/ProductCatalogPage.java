package com.shopsphere.product_service.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable JSON contract for a bounded product catalog response. */
public record ProductCatalogPage(
        List<ProductResponse> content,
        int number,
        int size,
        int totalPages,
        long totalElements) {

    public static ProductCatalogPage from(Page<ProductResponse> page) {
        return new ProductCatalogPage(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalPages(), page.getTotalElements());
    }
}
