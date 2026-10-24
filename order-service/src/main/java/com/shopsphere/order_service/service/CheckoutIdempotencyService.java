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
        CheckoutRequest checkout = repository
                .findByUserIdAndIdempotencyKey(userId, key)
                .orElseThrow(() ->
                        new IllegalStateException("Checkout key was not claimed"));

        checkout.setOrderId(orderId);
        checkout.setStatus(
                com.shopsphere.order_service.entity.CheckoutStatus.COMPLETED);
        return repository.save(checkout);
    }

    @Transactional
    public void markFailed(Long userId, String key) {
        repository.findByUserIdAndIdempotencyKey(userId, key)
                .ifPresent(checkout -> {
                    checkout.setStatus(
                            com.shopsphere.order_service.entity.CheckoutStatus.FAILED);
                    repository.save(checkout);
                });
    }
}
