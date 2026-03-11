package com.chat.chatserver.service;

import com.chat.chatserver.codec.WsMessageCodec;
import com.chat.chatserver.session.SessionManager;
import com.chat.common.constant.RedisKeys;
import com.chat.common.event.ChatMessageEvent;
import com.chat.common.event.PresenceChangeEvent;
import com.chat.common.util.JsonUtil;
import com.chat.common.ws.WsMessageType;
import com.chat.common.ws.WsOutboundMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSubscriberService implements MessageListener {

    private final SessionManager sessionManager;
    private final WsMessageCodec codec;

    @Value("${chat-server.server-id}")
    private String serverId;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());

        try {
            if (channel.equals(RedisKeys.chatServerChannel(serverId))) {
                handleChatMessage(body);
            } else if (channel.equals(RedisKeys.PRESENCE_CHANGES)) {
                handlePresenceChange(body);
            } else {
                log.warn("Received message on unknown channel: {}", channel);
            }
        } catch (Exception e) {
            log.error("Error processing Redis message on channel {}: {}", channel, e.getMessage(), e);
        }
    }

    private void handleChatMessage(String body) {
        ChatMessageEvent event = JsonUtil.fromJson(body, ChatMessageEvent.class);

        WsOutboundMessage outbound = WsOutboundMessage.builder()
                .type(WsMessageType.MESSAGE_RECEIVED)
                .payload(JsonUtil.toJsonNode(Map.of(
                        "messageId", event.getMessageId().toString(),
                        "channelId", event.getChannelId().toString(),
                        "senderId", event.getSenderId().toString(),
                        "senderName", event.getSenderName(),
                        "content", event.getContent(),
                        "messageType", event.getMessageType(),
                        "timestamp", event.getTimestamp().toString()
                )))
                .timestamp(Instant.now())
                .build();

        String encoded = codec.encode(outbound);

        if (event.getTargetUserIds() != null) {
            for (UUID targetUserId : event.getTargetUserIds()) {
                sessionManager.sendToUser(targetUserId, encoded);
            }
        }

        log.debug("Delivered message {} to {} local users",
                event.getMessageId(),
                event.getTargetUserIds() != null ? event.getTargetUserIds().size() : 0);
    }

    private void handlePresenceChange(String body) {
        PresenceChangeEvent event = JsonUtil.fromJson(body, PresenceChangeEvent.class);

        WsOutboundMessage outbound = WsOutboundMessage.builder()
                .type(WsMessageType.PRESENCE_UPDATE)
                .payload(JsonUtil.toJsonNode(Map.of(
                        "userId", event.getUserId().toString(),
                        "status", event.getNewStatus()
                )))
                .timestamp(Instant.now())
                .build();

        String encoded = codec.encode(outbound);

        // Broadcast presence update to all locally connected users
        for (UUID connectedUserId : sessionManager.getAllConnectedUserIds()) {
            sessionManager.sendToUser(connectedUserId, encoded);
        }

        log.debug("Broadcast presence update for user {} ({})",
                event.getUserId(), event.getNewStatus());
    }
}
