package com.shopsphere.inventory_service.service;

import com.shopsphere.inventory_service.entity.Inventory;
import com.shopsphere.inventory_service.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public List<Inventory> getAllInventory() {
        return inventoryRepository.findAll();
    }

    public Inventory getInventoryByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Inventory not found for product ID: " + productId));
    }

    @Transactional
    public Inventory createInventory(Inventory inventory) {
        if (inventoryRepository.findByProductId(inventory.getProductId()).isPresent()) {
            throw new IllegalArgumentException(
                    "Inventory already exists for product ID: " + inventory.getProductId());
        }

        if (inventory.getReservedQuantity() == null) {
            inventory.setReservedQuantity(0);
        }

        if (inventory.getReservedQuantity() > inventory.getQuantity()) {
            throw new IllegalArgumentException(
                    "Reserved quantity cannot exceed total quantity");
        }

        return inventoryRepository.save(inventory);
    }
}
