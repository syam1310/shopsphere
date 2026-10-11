package com.shopsphere.order_service.service;

import com.shopsphere.order_service.entity.CompensationStatus;
import com.shopsphere.order_service.entity.InventoryCompensation;
import com.shopsphere.order_service.repository.InventoryCompensationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryCompensationStateServiceTest {

    @Mock
    private InventoryCompensationRepository repository;

    @InjectMocks
    private InventoryCompensationStateService stateService;

    @Test
    void recordFailure_incrementsAttemptsAndSchedulesRetry() {
        InventoryCompensation compensation = new InventoryCompensation();
        compensation.setId(1L);
        compensation.setReservationId(UUID.randomUUID());
        compensation.setProductId(3L);
        compensation.setQuantity(1);
        compensation.setStatus(CompensationStatus.PENDING);
        compensation.setAttemptCount(0);
        compensation.setNextRetryAt(LocalDateTime.now());

        when(repository.findById(1L))
                .thenReturn(Optional.of(compensation));

        LocalDateTime before = LocalDateTime.now();

        stateService.recordFailure(
                1L,
                new RuntimeException("Inventory service unavailable")
        );

        LocalDateTime after = LocalDateTime.now();

        assertEquals(1, compensation.getAttemptCount());
        assertEquals(CompensationStatus.PENDING, compensation.getStatus());
        assertEquals(
                "RuntimeException: Inventory service unavailable",
                compensation.getLastError()
        );
        assertNotNull(compensation.getNextRetryAt());
        assertTrue(
                !compensation.getNextRetryAt().isBefore(before.plusSeconds(29))
                        && !compensation.getNextRetryAt().isAfter(after.plusSeconds(31)),
                "First retry should be scheduled approximately 30 seconds later"
        );

        verify(repository).saveAndFlush(compensation);
    }

    @Test
    void recordFailure_afterTenAttempts_marksFailed() {
        InventoryCompensation compensation = new InventoryCompensation();
        compensation.setId(1L);
        compensation.setReservationId(UUID.randomUUID());
        compensation.setProductId(3L);
        compensation.setQuantity(1);
        compensation.setStatus(CompensationStatus.PENDING);
        compensation.setAttemptCount(9);
        compensation.setNextRetryAt(LocalDateTime.now());

        when(repository.findById(1L))
                .thenReturn(Optional.of(compensation));

        stateService.recordFailure(
                1L,
                new RuntimeException("Inventory service unavailable")
        );

        assertEquals(10, compensation.getAttemptCount());
        assertEquals(CompensationStatus.FAILED, compensation.getStatus());
        verify(repository).saveAndFlush(compensation);
    }

    @Test
    void markCompleted_setsCompletedAndClearsLastError() {
        InventoryCompensation compensation = new InventoryCompensation();
        compensation.setId(1L);
        compensation.setReservationId(UUID.randomUUID());
        compensation.setProductId(3L);
        compensation.setQuantity(1);
        compensation.setStatus(CompensationStatus.PENDING);
        compensation.setAttemptCount(1);
        compensation.setLastError("Temporary inventory failure");

        when(repository.findById(1L))
                .thenReturn(Optional.of(compensation));

        stateService.markCompleted(1L);

        assertEquals(CompensationStatus.COMPLETED, compensation.getStatus());
        assertNull(compensation.getLastError());
        verify(repository).saveAndFlush(compensation);
    }
}