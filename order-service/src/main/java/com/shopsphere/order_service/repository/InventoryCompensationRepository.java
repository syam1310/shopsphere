package com.shopsphere.order_service.repository;

import com.shopsphere.order_service.entity.CompensationStatus;
import com.shopsphere.order_service.entity.InventoryCompensation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InventoryCompensationRepository
        extends JpaRepository<InventoryCompensation, Long> {

    Optional<InventoryCompensation> findByReservationId(
            java.util.UUID reservationId
    );

    List<InventoryCompensation> findTop100ByStatusAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            CompensationStatus status,
            LocalDateTime currentTime
    );

    @Modifying
    @Query("""
            UPDATE InventoryCompensation c
            SET c.status = :pending,
                c.claimedAt = null,
                c.nextRetryAt = :currentTime
            WHERE c.status = :processing
              AND c.claimedAt IS NOT NULL
              AND c.claimedAt <= :staleBefore
            """)
    int recoverStaleClaims(
            @Param("processing") CompensationStatus processing,
            @Param("pending") CompensationStatus pending,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("currentTime") LocalDateTime currentTime
    );

}