package com.shopsphere.inventory_service.controller;

import com.shopsphere.inventory_service.dto.CreateInventoryRequest;
import com.shopsphere.inventory_service.entity.Inventory;
import com.shopsphere.inventory_service.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<Inventory> getAllInventory() {
        return inventoryService.getAllInventory();
    }

    @GetMapping("/product/{productId}")
    public Inventory getInventoryByProductId(
            @PathVariable Long productId) {
        return inventoryService.getInventoryByProductId(productId);
    }

    @PostMapping
    public Inventory createInventory(
            @Valid @RequestBody CreateInventoryRequest request) {

        Inventory inventory = new Inventory();
        inventory.setProductId(request.getProductId());
        inventory.setQuantity(request.getQuantity());
        inventory.setReservedQuantity(
                request.getReservedQuantity() == null
                        ? 0
                        : request.getReservedQuantity()
        );

        return inventoryService.createInventory(inventory);
    }
}
