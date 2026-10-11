package com.shopsphere.order_service.service;

import com.shopsphere.order_service.entity.CompensationStatus;
import com.shopsphere.order_service.repository.InventoryCompensationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class InventoryCompensationRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(InventoryCompensationRecoveryService.class);

    private static final long STALE_CLAIM_TIMEOUT_MINUTES = 10;

    private final InventoryCompensationRepository repository;

    public InventoryCompensationRecoveryService(
            InventoryCompensationRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional
    public int recoverStaleClaims() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore =
                now.minusMinutes(STALE_CLAIM_TIMEOUT_MINUTES);

        int recovered = repository.recoverStaleClaims(
                CompensationStatus.PROCESSING,
                CompensationStatus.PENDING,
                staleBefore,
                now
        );

        if (recovered > 0) {
            log.warn("Recovered {} stale inventory compensation claims", recovered);
        }

        return recovered;
    }
}
