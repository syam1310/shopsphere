package com.shopsphere.order_service.service;

import com.shopsphere.order_service.entity.CheckoutRequest;
import com.shopsphere.order_service.repository.CheckoutRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CheckoutIdempotencyService {

    private final CheckoutRequestRepository repository;

    public CheckoutIdempotencyService(CheckoutRequestRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long userId, String key) {
        return repository.claimCheckoutKey(userId, key) == 1;
    }

    @Transactional(readOnly = true)
    public Optional<CheckoutRequest> find(Long userId, String key) {
        return repository.findByUserIdAndIdempotencyKey(userId, key);
    }

    @Transactional
    public CheckoutRequest markCompleted(
            Long userId, String key, Long orderId) {

        int updated = repository.markCompletedIfProcessing(
                userId, key, orderId);

        if (updated == 0) {
            CheckoutRequest checkout = repository
                    .findByUserIdAndIdempotencyKey(userId, key)
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Checkout key was not claimed"));

            if (checkout.getStatus()
                    == com.shopsphere.order_service.entity.CheckoutStatus.COMPLETED
                    && java.util.Objects.equals(checkout.getOrderId(), orderId)) {
                return checkout;
            }

            throw new IllegalStateException(
                    "Checkout cannot be completed from status: "
                            + checkout.getStatus());
        }

        return repository.findByUserIdAndIdempotencyKey(userId, key)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Completed checkout could not be reloaded"));
    }

    @Transactional
    public void markFailed(Long userId, String key) {
        repository.markFailedIfNotCompleted(userId, key);
    }
}
