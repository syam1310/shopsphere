package com.shopsphere.order_service.service;

import com.shopsphere.order_service.entity.CompensationStatus;
import com.shopsphere.order_service.repository.InventoryCompensationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryCompensationRecoveryServiceTest {

    @Mock
    private InventoryCompensationRepository repository;

    @InjectMocks
    private InventoryCompensationRecoveryService recoveryService;

    @Test
    void recoverStaleClaims_whenStaleClaimsExist_returnsRecoveredCount() {
        when(repository.recoverStaleClaims(
                eq(CompensationStatus.PROCESSING),
                eq(CompensationStatus.PENDING),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(2);

        int recovered = recoveryService.recoverStaleClaims();

        assertEquals(2, recovered);

        verify(repository).recoverStaleClaims(
                eq(CompensationStatus.PROCESSING),
                eq(CompensationStatus.PENDING),
                any(LocalDateTime.class),
                any(LocalDateTime.class));
    }

    @Test
    void recoverStaleClaims_usesTenMinuteStaleThreshold() {
        when(repository.recoverStaleClaims(
                eq(CompensationStatus.PROCESSING),
                eq(CompensationStatus.PENDING),
                any(LocalDateTime.class),
                any(LocalDateTime.class)))
                .thenReturn(0);

        recoveryService.recoverStaleClaims();

        ArgumentCaptor<LocalDateTime> staleBeforeCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> currentTimeCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);

        verify(repository).recoverStaleClaims(
                eq(CompensationStatus.PROCESSING),
                eq(CompensationStatus.PENDING),
                staleBeforeCaptor.capture(),
                currentTimeCaptor.capture());

        assertEquals(
                currentTimeCaptor.getValue().minusMinutes(10),
                staleBeforeCaptor.getValue());
    }

}