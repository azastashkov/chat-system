package com.chat.notification.service;

import com.chat.common.event.NotificationEvent;
import com.chat.notification.model.Notification;
import com.chat.notification.model.NotificationKey;
import com.chat.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void handleNotificationEvent_savesNotification() {
        UUID userId = UUID.randomUUID();
        NotificationEvent event = NotificationEvent.builder()
                .userId(userId)
                .type("NEW_MESSAGE")
                .payload("You have a new message")
                .timestamp(Instant.now())
                .build();

        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.handleNotificationEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getKey().getUserId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo("NEW_MESSAGE");
        assertThat(saved.getPayload()).isEqualTo("You have a new message");
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void getNotifications_unreadOnly_callsUnreadQuery() {
        UUID userId = UUID.randomUUID();
        Notification n1 = Notification.builder()
                .key(new NotificationKey(userId, UUID.randomUUID()))
                .type("NEW_MESSAGE")
                .payload("msg1")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findUnreadByUserId(userId, 20)).thenReturn(Arrays.asList(n1));

        var result = notificationService.getNotifications(userId, true, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo("NEW_MESSAGE");
        verify(notificationRepository).findUnreadByUserId(userId, 20);
        verify(notificationRepository, never()).findByUserId(any(), anyInt());
    }

    @Test
    void getNotifications_all_callsAllQuery() {
        UUID userId = UUID.randomUUID();
        when(notificationRepository.findByUserId(userId, 10)).thenReturn(Arrays.asList());

        notificationService.getNotifications(userId, false, 10);

        verify(notificationRepository).findByUserId(userId, 10);
        verify(notificationRepository, never()).findUnreadByUserId(any(), anyInt());
    }

    @Test
    void markAsRead_existingNotification_updatesReadFlag() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationKey key = new NotificationKey(userId, notificationId);
        Notification notification = Notification.builder()
                .key(key)
                .type("NEW_MESSAGE")
                .payload("test")
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        when(notificationRepository.findById(key)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.markAsRead(userId, notificationId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().isRead()).isTrue();
    }

    @Test
    void markAsRead_nonExistingNotification_doesNothing() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationKey key = new NotificationKey(userId, notificationId);

        when(notificationRepository.findById(key)).thenReturn(Optional.empty());

        notificationService.markAsRead(userId, notificationId);

        verify(notificationRepository, never()).save(any());
    }
}
