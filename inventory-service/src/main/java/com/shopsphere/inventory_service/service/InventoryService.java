package com.shopsphere.inventory_service.service;

import com.shopsphere.inventory_service.entity.Inventory;
import com.shopsphere.inventory_service.entity.InventoryReservation;
import com.shopsphere.inventory_service.entity.ReservationStatus;
import com.shopsphere.inventory_service.dto.InventoryAvailabilityResponse;
import com.shopsphere.inventory_service.repository.InventoryRepository;
import com.shopsphere.inventory_service.repository.InventoryReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryReservationRepository reservationRepository) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
    }

    public List<Inventory> getAllInventory() {
        return inventoryRepository.findAll();
    }

    public List<InventoryAvailabilityResponse> getAvailability(List<Long> productIds) {
        List<Inventory> rows = productIds == null || productIds.isEmpty()
                ? inventoryRepository.findAll()
                : inventoryRepository.findByProductIdIn(productIds);
        return rows.stream()
                .map(inventory -> new InventoryAvailabilityResponse(
                        inventory.getProductId(),
                        inventory.getQuantity() - inventory.getReservedQuantity()))
                .toList();
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

    // Legacy method retained for existing callers and tests.
    @Transactional
    public Inventory reserveInventory(Long productId, Integer requestedQuantity) {
        validateQuantity(requestedQuantity, "Reservation");

        Inventory inventory = getInventoryForUpdate(productId);
        int availableQuantity =
                inventory.getQuantity() - inventory.getReservedQuantity();

        if (requestedQuantity > availableQuantity) {
            throw new IllegalArgumentException(
                    "Insufficient available inventory for product ID: " + productId);
        }

        inventory.setReservedQuantity(
                inventory.getReservedQuantity() + requestedQuantity);

        return inventoryRepository.save(inventory);
    }

    // Idempotent reservation: retrying the same UUID won't reserve twice.
    @Transactional
    public Inventory reserveInventory(
            UUID reservationId, Long productId, Integer requestedQuantity) {

        if (reservationId == null) {
            throw new IllegalArgumentException("Reservation ID is required");
        }
        validateQuantity(requestedQuantity, "Reservation");

        Inventory inventory = getInventoryForUpdate(productId);

        InventoryReservation existing = reservationRepository
                .findByReservationId(reservationId)
                .orElse(null);

        if (existing != null) {
            validateMatchingReservation(existing, productId, requestedQuantity);

            if (existing.getStatus() == ReservationStatus.RELEASED) {
                throw new IllegalStateException(
                        "This reservation has already been released: " + reservationId);
            }

            return inventory;
        }

        int availableQuantity =
                inventory.getQuantity() - inventory.getReservedQuantity();

        if (requestedQuantity > availableQuantity) {
            throw new IllegalArgumentException(
                    "Insufficient available inventory for product ID: " + productId);
        }

        inventory.setReservedQuantity(
                inventory.getReservedQuantity() + requestedQuantity);
        inventoryRepository.save(inventory);

        InventoryReservation reservation = new InventoryReservation();
        reservation.setReservationId(reservationId);
        reservation.setProductId(productId);
        reservation.setQuantity(requestedQuantity);
        reservation.setStatus(ReservationStatus.RESERVED);
        reservationRepository.save(reservation);

        return inventory;
    }

    // Legacy release method retained for older callers.
    @Transactional
    public Inventory releaseInventory(Long productId, Integer quantityToRelease) {
        validateQuantity(quantityToRelease, "Release");

        Inventory inventory = getInventoryForUpdate(productId);

        if (quantityToRelease > inventory.getReservedQuantity()) {
            throw new IllegalArgumentException(
                    "Cannot release more stock than is reserved for product ID: "
                            + productId);
        }

        inventory.setReservedQuantity(
                inventory.getReservedQuantity() - quantityToRelease);

        return inventoryRepository.save(inventory);
    }

    // Idempotent release: a repeated release won't decrement stock again.
    @Transactional
    public Inventory releaseInventory(
            UUID reservationId, Long productId, Integer quantityToRelease) {

        if (reservationId == null) {
            throw new IllegalArgumentException("Reservation ID is required");
        }
        validateQuantity(quantityToRelease, "Release");

        // Reserve and release lock inventory before the reservation row. A release
        // can therefore safely create a tombstone when a reserve response was lost:
        // a late retry using the same UUID will see RELEASED and cannot consume stock.
        Inventory inventory = getInventoryForUpdate(productId);
        InventoryReservation reservation = reservationRepository
                .findByReservationIdForUpdate(reservationId)
                .orElse(null);

        if (reservation == null) {
            reservation = new InventoryReservation();
            reservation.setReservationId(reservationId);
            reservation.setProductId(productId);
            reservation.setQuantity(quantityToRelease);
            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepository.save(reservation);
            return inventory;
        }

        validateMatchingReservation(reservation, productId, quantityToRelease);

        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return inventory;
        }

        if (reservation.getQuantity() > inventory.getReservedQuantity()) {
            throw new IllegalStateException(
                    "Reserved stock is inconsistent for product ID: "
                            + reservation.getProductId());
        }

        inventory.setReservedQuantity(
                inventory.getReservedQuantity() - reservation.getQuantity());

        reservation.setStatus(ReservationStatus.RELEASED);

        inventoryRepository.save(inventory);
        reservationRepository.save(reservation);

        return inventory;
    }

    private Inventory getInventoryForUpdate(Long productId) {
        return inventoryRepository.findByProductIdForUpdate(productId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Inventory not found for product ID: " + productId));
    }

    private void validateQuantity(Integer quantity, String operation) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException(
                    operation + " quantity must be greater than zero");
        }
    }

    private void validateMatchingReservation(
            InventoryReservation reservation,
            Long productId,
            Integer quantity) {

        if (!reservation.getProductId().equals(productId)
                || !reservation.getQuantity().equals(quantity)) {
            throw new IllegalArgumentException(
                    "Reservation ID does not match the requested product and quantity");
        }
    }
}
