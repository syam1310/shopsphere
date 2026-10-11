package com.shopsphere.order_service.repository.impl;

import com.shopsphere.order_service.entity.CompensationStatus;
import com.shopsphere.order_service.entity.InventoryCompensation;
import com.shopsphere.order_service.repository.InventoryCompensationClaimRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class InventoryCompensationClaimRepositoryImpl
        implements InventoryCompensationClaimRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public List<InventoryCompensation> claimPendingCompensations(
            LocalDateTime currentTime,
            int batchSize
    ) {
        Query query = entityManager.createNativeQuery("""
                SELECT id
                FROM inventory_compensations
                WHERE status = :status
                  AND next_retry_at <= :currentTime
                ORDER BY next_retry_at ASC
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """);

        query.setParameter("status", CompensationStatus.PENDING.name());
        query.setParameter("currentTime", currentTime);
        query.setParameter("batchSize", batchSize);

        @SuppressWarnings("unchecked")
        List<Number> ids = query.getResultList();

        if (ids.isEmpty()) {
            return List.of();
        }

        List<Long> compensationIds = ids.stream()
                .map(Number::longValue)
                .toList();

        List<InventoryCompensation> compensations = entityManager.createQuery("""
                        SELECT c
                        FROM InventoryCompensation c
                        WHERE c.id IN :ids
                        ORDER BY c.nextRetryAt ASC
                        """, InventoryCompensation.class)
                .setParameter("ids", compensationIds)
                .getResultList();

        LocalDateTime claimedAt = LocalDateTime.now();

        for (InventoryCompensation compensation : compensations) {
            compensation.setStatus(CompensationStatus.PROCESSING);
            compensation.setClaimedAt(claimedAt);
        }

        entityManager.flush();

        return compensations;
    }
}
