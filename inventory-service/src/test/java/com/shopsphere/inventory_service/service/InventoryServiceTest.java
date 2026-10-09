package com.shopsphere.inventory_service.service;

import com.shopsphere.inventory_service.entity.Inventory;
import com.shopsphere.inventory_service.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory inventory;

    @BeforeEach
    void setUp() {
        inventory = new Inventory();
        inventory.setProductId(1001L);
        inventory.setQuantity(20);
        inventory.setReservedQuantity(5);
    }

    @Test
    void createInventory_shouldSaveInventory_whenProductDoesNotExist() {
        when(inventoryRepository.findByProductId(1001L))
                .thenReturn(Optional.empty());

        when(inventoryRepository.save(inventory))
                .thenReturn(inventory);

        Inventory result = inventoryService.createInventory(inventory);

        assertNotNull(result);
        assertEquals(1001L, result.getProductId());
        assertEquals(20, result.getQuantity());
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void createInventory_shouldThrowException_whenProductAlreadyExists() {
        when(inventoryRepository.findByProductId(1001L))
                .thenReturn(Optional.of(inventory));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> inventoryService.createInventory(inventory)
        );

        assertEquals(
                "Inventory already exists for product ID: 1001",
                exception.getMessage()
        );

        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    void createInventory_shouldThrowException_whenReservedExceedsQuantity() {
        inventory.setQuantity(10);
        inventory.setReservedQuantity(12);

        when(inventoryRepository.findByProductId(1001L))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> inventoryService.createInventory(inventory)
        );

        assertEquals(
                "Reserved quantity cannot exceed total quantity",
                exception.getMessage()
        );

        verify(inventoryRepository, never()).save(any(Inventory.class));
    }
}
