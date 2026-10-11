package com.shopsphere.order_service.service;

import com.shopsphere.order_service.client.InventoryClient;
import com.shopsphere.order_service.entity.CompensationStatus;
import com.shopsphere.order_service.entity.InventoryCompensation;
import com.shopsphere.order_service.repository.InventoryCompensationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryCompensationRetryServiceTest {

    @Mock
    private InventoryCompensationRepository repository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private InventoryCompensationStateService stateService;

    @InjectMocks
    private InventoryCompensationRetryService retryService;

    @Test
    void processCompensation_whenReleaseSucceeds_marksCompleted() {
        InventoryCompensation compensation = new InventoryCompensation();
        compensation.setId(1L);
        compensation.setReservationId(UUID.randomUUID());
        compensation.setProductId(3L);
        compensation.setQuantity(1);
        compensation.setStatus(CompensationStatus.PENDING);
        compensation.setAttemptCount(0);

        retryService.processCompensation(compensation);

        verify(inventoryClient).releaseInventory(
                compensation.getReservationId(),
                3L,
                1
        );

        verify(stateService).markCompleted(compensation.getId());
        verify(stateService, never()).recordFailure(anyLong(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void processCompensation_whenReleaseFails_recordsFailure() {
        InventoryCompensation compensation = new InventoryCompensation();
        compensation.setId(1L);
        compensation.setReservationId(UUID.randomUUID());
        compensation.setProductId(3L);
        compensation.setQuantity(1);
        compensation.setStatus(CompensationStatus.PENDING);
        compensation.setAttemptCount(0);

        doThrow(new RuntimeException("Inventory service unavailable"))
                .when(inventoryClient)
                .releaseInventory(compensation.getReservationId(), 3L, 1);

        retryService.processCompensation(compensation);

        verify(stateService).recordFailure(
                eq(compensation.getId()),
                any(RuntimeException.class)
        );

        verify(stateService, never()).markCompleted(anyLong());
        verify(repository, never()).save(any());
    }
}
