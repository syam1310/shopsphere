package com.shopsphere.order_service.service;

import com.shopsphere.order_service.entity.CompensationStatus;
import com.shopsphere.order_service.entity.InventoryCompensation;
import com.shopsphere.order_service.repository.InventoryCompensationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class InventoryCompensationService {

    private final InventoryCompensationRepository repository;

    public InventoryCompensationService(
            InventoryCompensationRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedRelease(
            UUID reservationId,
            Long productId,
            Integer quantity,
            RuntimeException releaseException
    ) {
        InventoryCompensation compensation = repository
                .findByReservationId(reservationId)
                .orElseGet(InventoryCompensation::new);

        compensation.setReservationId(reservationId);
        compensation.setProductId(productId);
        compensation.setQuantity(quantity);
        compensation.setStatus(CompensationStatus.PENDING);
        compensation.setNextRetryAt(LocalDateTime.now());
        compensation.setLastError(truncateError(releaseException));

        if (compensation.getAttemptCount() == null) {
            compensation.setAttemptCount(0);
        }

        repository.saveAndFlush(compensation);
    }

    private String truncateError(RuntimeException exception) {
        String message = exception.getClass().getSimpleName()
                + ": " + exception.getMessage();

        return message.length() > 2000
                ? message.substring(0, 2000)
                : message;
    }
}