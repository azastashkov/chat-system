package com.chat.notification.service;

import com.chat.common.event.NotificationEvent;
import com.chat.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final NotificationService notificationService;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            String json = message.getValue().get("data");
            NotificationEvent event = JsonUtil.fromJson(json, NotificationEvent.class);
            log.debug("Received notification event from stream for user {}", event.getUserId());
            notificationService.handleNotificationEvent(event);
        } catch (Exception e) {
            log.error("Failed to process notification from stream", e);
        }
    }
}
