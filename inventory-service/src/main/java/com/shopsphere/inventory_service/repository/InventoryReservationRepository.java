package com.shopsphere.inventory_service.repository;

import com.shopsphere.inventory_service.entity.InventoryReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationRepository
        extends JpaRepository<InventoryReservation, Long> {

    Optional<InventoryReservation> findByReservationId(UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r FROM InventoryReservation r
            WHERE r.reservationId = :reservationId
            """)
    Optional<InventoryReservation> findByReservationIdForUpdate(
            @Param("reservationId") UUID reservationId);
}
