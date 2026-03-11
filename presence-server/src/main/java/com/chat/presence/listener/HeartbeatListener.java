package com.chat.presence.listener;

import com.chat.common.util.JsonUtil;
import com.chat.presence.service.HeartbeatMonitor;
import com.chat.presence.service.PresenceService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatListener implements MessageListener {

    private final PresenceService presenceService;
    private final HeartbeatMonitor heartbeatMonitor;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            JsonNode node = JsonUtil.mapper().readTree(body);
            UUID userId = UUID.fromString(node.get("userId").asText());

            log.debug("Received heartbeat for user {}", userId);
            presenceService.handleHeartbeat(userId);
            heartbeatMonitor.trackUser(userId);
        } catch (Exception e) {
            log.error("Failed to process heartbeat message", e);
        }
    }
}
