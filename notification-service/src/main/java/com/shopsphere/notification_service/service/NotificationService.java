package com.shopsphere.notification_service.service;

import com.shopsphere.notification_service.dto.CreateNotificationRequest;
import com.shopsphere.notification_service.entity.Notification;
import com.shopsphere.notification_service.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Notification create(CreateNotificationRequest request) {
        Notification notification = new Notification();
        notification.setUserId(request.getUserId());
        notification.setTitle(request.getTitle());
        notification.setMessage(request.getMessage());
        notification.setType(request.getType());

        return repository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> getByUserId(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getUnreadByUserId(Long userId) {
        return repository.findByUserIdAndReadFalseOrderByCreatedAtDesc(
                userId);
    }

    @Transactional
    public Notification markAsRead(Long notificationId) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Notification not found"));

        notification.setRead(true);
        return repository.save(notification);
    }

    @Transactional
    public void delete(Long notificationId) {
        if (!repository.existsById(notificationId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Notification not found");
        }

        repository.deleteById(notificationId);
    }
}
