package com.chat.notification.controller;

import com.chat.common.dto.NotificationDto;
import com.chat.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/{userId}")
    public ResponseEntity<List<NotificationDto>> getNotifications(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "true") boolean unreadOnly,
            @RequestParam(defaultValue = "20") int limit) {
        List<NotificationDto> notifications = notificationService.getNotifications(userId, unreadOnly, limit);
        return ResponseEntity.ok(notifications);
    }

    @PutMapping("/{userId}/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID userId,
            @PathVariable UUID notificationId) {
        notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.ok().build();
    }
}
