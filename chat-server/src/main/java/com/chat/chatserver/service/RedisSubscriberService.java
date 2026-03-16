package com.chat.chatserver.service;

import com.chat.chatserver.cache.ChannelMemberCache;
import com.chat.chatserver.codec.WsMessageCodec;
import com.chat.chatserver.session.SessionManager;
import com.chat.common.constant.RedisKeys;
import com.chat.common.event.PresenceChangeEvent;
import com.chat.common.event.TypingEvent;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSubscriberService implements MessageListener {

    private final SessionManager sessionManager;
    private final WsMessageCodec codec;
    private final ChannelMemberCache channelMemberCache;

    @Value("${chat-server.server-id}")
    private String serverId;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());

        try {
            if (channel.equals(RedisKeys.PRESENCE_CHANGES)) {
                handlePresenceChange(body);
            } else if (channel.startsWith("typing:")) {
                handleTypingEvent(body);
            } else if (channel.equals(RedisKeys.CHANNEL_MEMBERSHIP_INVALIDATE)) {
                handleCacheInvalidation(body);
            } else {
                log.warn("Received message on unknown channel: {}", channel);
            }
        } catch (Exception e) {
            log.error("Error processing Redis message on channel {}: {}", channel, e.getMessage(), e);
        }
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

        for (UUID connectedUserId : sessionManager.getAllConnectedUserIds()) {
            sessionManager.sendToUser(connectedUserId, encoded);
        }

        log.debug("Broadcast presence update for user {} ({})",
                event.getUserId(), event.getNewStatus());
    }

    private void handleTypingEvent(String body) {
        TypingEvent event = JsonUtil.fromJson(body, TypingEvent.class);

        // Skip if from this server (already delivered locally)
        if (serverId.equals(event.getOriginServerId())) {
            return;
        }

        WsOutboundMessage outbound = WsOutboundMessage.builder()
                .type(WsMessageType.TYPING)
                .payload(JsonUtil.toJsonNode(Map.of(
                        "channelId", event.getChannelId().toString(),
                        "userId", event.getUserId().toString(),
                        "username", event.getUsername()
                )))
                .timestamp(Instant.now())
                .build();

        String encoded = codec.encode(outbound);

        // Scope delivery to channel members
        List<UUID> members = channelMemberCache.getMembers(event.getChannelId());
        if (members != null) {
            for (UUID memberId : members) {
                if (!memberId.equals(event.getUserId())) {
                    sessionManager.sendToUser(memberId, encoded);
                }
            }
        } else {
            // Fallback: broadcast to all local users
            for (UUID connectedUserId : sessionManager.getAllConnectedUserIds()) {
                if (!connectedUserId.equals(event.getUserId())) {
                    sessionManager.sendToUser(connectedUserId, encoded);
                }
            }
        }
    }

    private void handleCacheInvalidation(String body) {
        try {
            UUID channelId = UUID.fromString(body);
            channelMemberCache.invalidate(channelId);
            log.debug("Invalidated channel member cache for channel {}", channelId);
        } catch (Exception e) {
            log.warn("Failed to process cache invalidation: {}", e.getMessage());
        }
    }
}
