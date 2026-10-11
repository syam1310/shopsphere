package com.shopsphere.order_service.repository;

import com.shopsphere.order_service.entity.InventoryCompensation;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryCompensationClaimRepository {

    List<InventoryCompensation> claimPendingCompensations(
            LocalDateTime currentTime,
            int batchSize
    );
}
