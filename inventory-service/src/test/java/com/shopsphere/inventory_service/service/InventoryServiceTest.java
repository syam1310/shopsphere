package com.shopsphere.inventory_service.service;

import com.shopsphere.inventory_service.entity.Inventory;
import com.shopsphere.inventory_service.entity.InventoryReservation;
import com.shopsphere.inventory_service.entity.ReservationStatus;
import com.shopsphere.inventory_service.repository.InventoryRepository;
import com.shopsphere.inventory_service.repository.InventoryReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

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
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

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

        assertEquals(
                "Inventory already exists for product ID: 1001",
                assertThrows(IllegalArgumentException.class,
                        () -> inventoryService.createInventory(inventory)).getMessage()
        );

        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    void createInventory_shouldThrowException_whenReservedExceedsQuantity() {
        inventory.setQuantity(10);
        inventory.setReservedQuantity(12);
        when(inventoryRepository.findByProductId(1001L))
                .thenReturn(Optional.empty());

        assertEquals(
                "Reserved quantity cannot exceed total quantity",
                assertThrows(IllegalArgumentException.class,
                        () -> inventoryService.createInventory(inventory)).getMessage()
        );

        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    void reserveInventory_shouldIncreaseReservedQuantity() {
        when(inventoryRepository.findByProductIdForUpdate(1001L))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

        Inventory result = inventoryService.reserveInventory(1001L, 3);

        assertEquals(8, result.getReservedQuantity());
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void reserveInventory_shouldRejectInsufficientStock() {
        inventory.setReservedQuantity(18);
        when(inventoryRepository.findByProductIdForUpdate(1001L))
                .thenReturn(Optional.of(inventory));

        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.reserveInventory(1001L, 3));

        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    void releaseInventory_shouldDecreaseReservedQuantity() {
        when(inventoryRepository.findByProductIdForUpdate(1001L))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

        Inventory result = inventoryService.releaseInventory(1001L, 2);

        assertEquals(3, result.getReservedQuantity());
        verify(inventoryRepository).save(inventory);
    }

    @Test
    void reserveInventory_withSameReservationId_shouldNotReserveTwice() {
        UUID reservationId = UUID.randomUUID();

        when(inventoryRepository.findByProductIdForUpdate(1001L))
                .thenReturn(Optional.of(inventory));
        when(reservationRepository.findByReservationId(reservationId))
                .thenReturn(Optional.empty());

        when(reservationRepository.save(any(InventoryReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);

        inventoryService.reserveInventory(reservationId, 1001L, 3);

        assertEquals(8, inventory.getReservedQuantity());

        when(reservationRepository.findByReservationId(reservationId))
                .thenReturn(Optional.of(reservedReservation(reservationId, 1001L, 3)));

        inventoryService.reserveInventory(reservationId, 1001L, 3);

        assertEquals(8, inventory.getReservedQuantity());
        verify(inventoryRepository, times(1)).save(inventory);
        verify(reservationRepository, times(1))
                .save(any(InventoryReservation.class));
    }

    @Test
    void getAvailability_forRequestedProducts_returnsOnlyAvailableQuantities() {
        Inventory second = new Inventory();
        second.setProductId(1002L);
        second.setQuantity(10);
        second.setReservedQuantity(4);
        when(inventoryRepository.findByProductIdIn(List.of(1001L, 1002L)))
                .thenReturn(List.of(inventory, second));

        var result = inventoryService.getAvailability(List.of(1001L, 1002L));

        assertEquals(2, result.size());
        assertEquals(15, result.get(0).availableQuantity());
        assertEquals(6, result.get(1).availableQuantity());
        verify(inventoryRepository).findByProductIdIn(List.of(1001L, 1002L));
        verify(inventoryRepository, never()).findAll();
    }

    @Test
    void releaseInventory_withSameReservationId_shouldNotReleaseTwice() {
        UUID reservationId = UUID.randomUUID();
        InventoryReservation reservation =
                reservedReservation(reservationId, 1001L, 3);

        when(reservationRepository.findByReservationIdForUpdate(reservationId))
                .thenReturn(Optional.of(reservation), Optional.of(reservation));
        when(inventoryRepository.findByProductIdForUpdate(1001L))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(inventory)).thenReturn(inventory);
        when(reservationRepository.save(reservation)).thenReturn(reservation);

        inventoryService.releaseInventory(reservationId, 1001L, 3);

        assertEquals(2, inventory.getReservedQuantity());
        assertEquals(ReservationStatus.RELEASED, reservation.getStatus());

        inventoryService.releaseInventory(reservationId, 1001L, 3);

        assertEquals(2, inventory.getReservedQuantity());
        verify(inventoryRepository, times(1)).save(inventory);
    }

    @Test
    void releaseInventory_whenReservationIsNotYetVisible_createsTombstone() {
        UUID reservationId = UUID.randomUUID();
        when(inventoryRepository.findByProductIdForUpdate(1001L))
                .thenReturn(Optional.of(inventory));
        when(reservationRepository.findByReservationIdForUpdate(reservationId))
                .thenReturn(Optional.empty());
        when(reservationRepository.save(any(InventoryReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Inventory result = inventoryService.releaseInventory(reservationId, 1001L, 3);

        assertSame(inventory, result);
        assertEquals(5, inventory.getReservedQuantity());
        ArgumentCaptor<InventoryReservation> reservationCaptor =
                ArgumentCaptor.forClass(InventoryReservation.class);
        verify(reservationRepository).save(reservationCaptor.capture());
        assertEquals(reservationId, reservationCaptor.getValue().getReservationId());
        assertEquals(1001L, reservationCaptor.getValue().getProductId());
        assertEquals(3, reservationCaptor.getValue().getQuantity());
        assertEquals(ReservationStatus.RELEASED, reservationCaptor.getValue().getStatus());
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    void reserveInventory_whenReservationWasAlreadyReleased_rejectsLateRetry() {
        UUID reservationId = UUID.randomUUID();
        InventoryReservation tombstone = reservedReservation(reservationId, 1001L, 3);
        tombstone.setStatus(ReservationStatus.RELEASED);
        when(inventoryRepository.findByProductIdForUpdate(1001L))
                .thenReturn(Optional.of(inventory));
        when(reservationRepository.findByReservationId(reservationId))
                .thenReturn(Optional.of(tombstone));

        assertThrows(IllegalStateException.class,
                () -> inventoryService.reserveInventory(reservationId, 1001L, 3));

        assertEquals(5, inventory.getReservedQuantity());
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    void reserveInventory_shouldRejectReservationIdWithDifferentQuantity() {
        UUID reservationId = UUID.randomUUID();

        when(inventoryRepository.findByProductIdForUpdate(1001L))
                .thenReturn(Optional.of(inventory));
        when(reservationRepository.findByReservationId(reservationId))
                .thenReturn(Optional.of(
                        reservedReservation(reservationId, 1001L, 3)));

        assertThrows(IllegalArgumentException.class,
                () -> inventoryService.reserveInventory(reservationId, 1001L, 4));

        assertEquals(5, inventory.getReservedQuantity());
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    private InventoryReservation reservedReservation(
            UUID reservationId, Long productId, Integer quantity) {

        InventoryReservation reservation = new InventoryReservation();
        reservation.setReservationId(reservationId);
        reservation.setProductId(productId);
        reservation.setQuantity(quantity);
        reservation.setStatus(ReservationStatus.RESERVED);
        return reservation;
    }
}
