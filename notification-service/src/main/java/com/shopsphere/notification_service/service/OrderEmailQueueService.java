package com.shopsphere.notification_service.service;

import com.shopsphere.notification_service.dto.OrderConfirmationRequest;
import com.shopsphere.notification_service.entity.EmailJob;
import com.shopsphere.notification_service.repository.EmailJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderEmailQueueService {

    private final EmailJobRepository repository;

    public OrderEmailQueueService(EmailJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void enqueue(OrderConfirmationRequest request) {
        String eventKey = "ORDER_CONFIRMED:" + request.orderId();

        if (repository.existsByEventKey(eventKey)) {
            return;
        }

        EmailJob job = new EmailJob();
        job.eventKey = eventKey;
        job.recipient = request.email();
        job.subject = "ShopSphere - Order #" + request.orderId() + " Confirmed";
        job.message = """
                Thank you for shopping with ShopSphere!

                Your order has been confirmed.

                Order ID: %s
                Order Total: %s
                Status: CONFIRMED

                Thank you,
                ShopSphere Team
                """.formatted(request.orderId(), request.totalAmount());

        repository.save(job);
    }
}
