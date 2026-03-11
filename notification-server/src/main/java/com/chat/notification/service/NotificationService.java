package com.chat.notification.service;

import com.chat.common.dto.NotificationDto;
import com.chat.common.event.NotificationEvent;
import com.chat.common.util.TimeUuidUtil;
import com.chat.notification.model.Notification;
import com.chat.notification.model.NotificationKey;
import com.chat.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public void handleNotificationEvent(NotificationEvent event) {
        UUID notificationId = TimeUuidUtil.now();

        Notification notification = Notification.builder()
                .key(new NotificationKey(event.getUserId(), notificationId))
                .type(event.getType())
                .payload(event.getPayload())
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        notificationRepository.save(notification);
        log.info("[PUSH] Sending push notification to user {} : {}", event.getUserId(), event.getPayload());
    }

    public List<NotificationDto> getNotifications(UUID userId, boolean unreadOnly, int limit) {
        List<Notification> notifications;
        if (unreadOnly) {
            notifications = notificationRepository.findUnreadByUserId(userId, limit);
        } else {
            notifications = notificationRepository.findByUserId(userId, limit);
        }

        return notifications.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public void markAsRead(UUID userId, UUID notificationId) {
        NotificationKey key = new NotificationKey(userId, notificationId);
        Optional<Notification> optNotification = notificationRepository.findById(key);

        optNotification.ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
            log.debug("Marked notification {} as read for user {}", notificationId, userId);
        });
    }

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .notificationId(notification.getKey().getNotificationId())
                .userId(notification.getKey().getUserId())
                .type(notification.getType())
                .payload(notification.getPayload())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
