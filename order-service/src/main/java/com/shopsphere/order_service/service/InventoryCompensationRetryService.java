package com.shopsphere.order_service.service;

import com.shopsphere.order_service.client.InventoryClient;
import com.shopsphere.order_service.entity.InventoryCompensation;
import com.shopsphere.order_service.repository.InventoryCompensationClaimRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InventoryCompensationRetryService {

    private static final Logger log =
            LoggerFactory.getLogger(InventoryCompensationRetryService.class);

    private static final int BATCH_SIZE = 100;

    private final InventoryCompensationClaimRepository claimRepository;
    private final InventoryClient inventoryClient;
    private final InventoryCompensationStateService stateService;
    private final InventoryCompensationRecoveryService recoveryService;

    public InventoryCompensationRetryService(
            InventoryCompensationClaimRepository claimRepository,
            InventoryClient inventoryClient,
            InventoryCompensationStateService stateService,
            InventoryCompensationRecoveryService recoveryService
    ) {
        this.claimRepository = claimRepository;
        this.inventoryClient = inventoryClient;
        this.stateService = stateService;
        this.recoveryService = recoveryService;
    }

    @Scheduled(fixedDelay = 30000)
    public void retryPendingCompensations() {
        recoveryService.recoverStaleClaims();

        List<InventoryCompensation> claimed =
                claimRepository.claimPendingCompensations(
                        LocalDateTime.now(),
                        BATCH_SIZE
                );

        for (InventoryCompensation compensation : claimed) {
            processCompensation(compensation);
        }
    }

    public void processCompensation(InventoryCompensation compensation) {
        try {
            inventoryClient.releaseInventory(
                    compensation.getReservationId(),
                    compensation.getProductId(),
                    compensation.getQuantity()
            );

            stateService.markCompleted(compensation.getId());

            log.info(
                    "Inventory compensation completed for reservation {}",
                    compensation.getReservationId()
            );

        } catch (RuntimeException exception) {
            try {
                stateService.recordFailure(compensation.getId(), exception);
            } catch (RuntimeException persistenceException) {
                exception.addSuppressed(persistenceException);

                log.error(
                        "Could not persist retry state for reservation {}",
                        compensation.getReservationId(),
                        exception
                );
            }
        }
    }
}
