package com.shopsphere.order_service.service;

import com.shopsphere.order_service.entity.CheckoutRequest;
import com.shopsphere.order_service.entity.CheckoutStatus;
import com.shopsphere.order_service.repository.CheckoutRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutIdempotencyServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final String KEY = "test-checkout-key";

    @Mock
    private CheckoutRequestRepository repository;

    @InjectMocks
    private CheckoutIdempotencyService service;

    @Test
    void markFailed_usesConditionalDatabaseUpdate() {
        when(repository.markFailedIfNotCompleted(USER_ID, KEY))
                .thenReturn(1);

        service.markFailed(USER_ID, KEY);

        verify(repository).markFailedIfNotCompleted(USER_ID, KEY);
        verify(repository, never()).save(any());
    }

    @Test
    void markFailed_doesNotChangeCompletedCheckout() {
        when(repository.markFailedIfNotCompleted(USER_ID, KEY))
                .thenReturn(0);

        assertDoesNotThrow(() -> service.markFailed(USER_ID, KEY));

        verify(repository).markFailedIfNotCompleted(USER_ID, KEY);
        verify(repository, never()).save(any());
    }

    @Test
    void markCompleted_updatesOnlyProcessingCheckout() {
        when(repository.markCompletedIfProcessing(
                USER_ID, KEY, ORDER_ID))
                .thenReturn(1);

        CheckoutRequest completed = mock(CheckoutRequest.class);
        when(repository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(completed));

        assertSame(
                completed,
                service.markCompleted(USER_ID, KEY, ORDER_ID));

        verify(repository).markCompletedIfProcessing(
                USER_ID, KEY, ORDER_ID);
        verify(repository).findByUserIdAndIdempotencyKey(USER_ID, KEY);
        verify(repository, never()).save(any());
    }

    @Test
    void markCompleted_isIdempotentForSameCompletedOrder() {
        when(repository.markCompletedIfProcessing(
                USER_ID, KEY, ORDER_ID))
                .thenReturn(0);

        CheckoutRequest existing = mock(CheckoutRequest.class);
        when(existing.getStatus()).thenReturn(CheckoutStatus.COMPLETED);
        when(existing.getOrderId()).thenReturn(ORDER_ID);
        when(repository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(existing));

        assertSame(
                existing,
                service.markCompleted(USER_ID, KEY, ORDER_ID));

        verify(repository, never()).save(any());
    }

    @Test
    void markCompleted_rejectsDifferentOrderForCompletedCheckout() {
        when(repository.markCompletedIfProcessing(
                USER_ID, KEY, ORDER_ID))
                .thenReturn(0);

        CheckoutRequest existing = mock(CheckoutRequest.class);
        when(existing.getStatus()).thenReturn(CheckoutStatus.COMPLETED);
        when(existing.getOrderId()).thenReturn(200L);
        when(repository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(existing));

        assertThrows(
                IllegalStateException.class,
                () -> service.markCompleted(USER_ID, KEY, ORDER_ID));

        verify(repository, never()).save(any());
    }

    @Test
    void markCompleted_rejectsFailedCheckout() {
        when(repository.markCompletedIfProcessing(
                USER_ID, KEY, ORDER_ID))
                .thenReturn(0);

        CheckoutRequest existing = mock(CheckoutRequest.class);
        when(existing.getStatus()).thenReturn(CheckoutStatus.FAILED);
        when(repository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                .thenReturn(Optional.of(existing));

        assertThrows(
                IllegalStateException.class,
                () -> service.markCompleted(USER_ID, KEY, ORDER_ID));

        verify(repository, never()).save(any());
    }
}
