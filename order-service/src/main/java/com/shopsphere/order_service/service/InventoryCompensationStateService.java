package com.shopsphere.order_service.service;

import com.shopsphere.order_service.entity.CompensationStatus;
import com.shopsphere.order_service.entity.InventoryCompensation;
import com.shopsphere.order_service.repository.InventoryCompensationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class InventoryCompensationStateService {

    private final InventoryCompensationRepository repository;

    public InventoryCompensationStateService(
            InventoryCompensationRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long id) {
        InventoryCompensation compensation = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Compensation record not found: " + id));

        compensation.setStatus(CompensationStatus.COMPLETED);
        compensation.setClaimedAt(null);
        compensation.setLastError(null);
        repository.saveAndFlush(compensation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long id, RuntimeException exception) {
        InventoryCompensation compensation = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Compensation record not found: " + id));

        int attempts = compensation.getAttemptCount() + 1;
        compensation.setAttemptCount(attempts);
        compensation.setClaimedAt(null);
        compensation.setLastError(truncateError(exception));

        if (attempts >= 10) {
            compensation.setStatus(CompensationStatus.FAILED);
        } else {
            compensation.setStatus(CompensationStatus.PENDING);

            long delaySeconds = Math.min(
                    30L * (1L << Math.min(attempts - 1, 6)),
                    1800L
            );

            compensation.setNextRetryAt(
                    LocalDateTime.now().plusSeconds(delaySeconds)
            );
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