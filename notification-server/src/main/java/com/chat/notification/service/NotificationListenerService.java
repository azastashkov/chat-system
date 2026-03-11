package com.chat.notification.service;

import com.chat.common.event.NotificationEvent;
import com.chat.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListenerService implements MessageListener {

    private final NotificationService notificationService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            NotificationEvent event = JsonUtil.fromJson(body, NotificationEvent.class);
            log.debug("Received notification event for user {}", event.getUserId());
            notificationService.handleNotificationEvent(event);
        } catch (Exception e) {
            log.error("Failed to process notification message", e);
        }
    }
}
