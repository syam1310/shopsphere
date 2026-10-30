package com.shopsphere.notification_service.controller;

import com.shopsphere.notification_service.dto.CreateNotificationRequest;
import com.shopsphere.notification_service.entity.Notification;
import com.shopsphere.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Notification create(
            @Valid @RequestBody CreateNotificationRequest request) {
        return service.create(request);
    }

    @GetMapping("/user/{userId}")
    public List<Notification> getByUserId(@PathVariable Long userId) {
        return service.getByUserId(userId);
    }

    @GetMapping("/user/{userId}/unread")
    public List<Notification> getUnreadByUserId(
            @PathVariable Long userId) {
        return service.getUnreadByUserId(userId);
    }

    @PatchMapping("/{notificationId}/read")
    public Notification markAsRead(
            @PathVariable Long notificationId) {
        return service.markAsRead(notificationId);
    }

    @DeleteMapping("/{notificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long notificationId) {
        service.delete(notificationId);
    }
}
