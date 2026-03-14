package com.chat.chatserver.handler;

import com.chat.chatserver.codec.WsMessageCodec;
import com.chat.chatserver.metrics.ChatMetrics;
import com.chat.chatserver.service.MessageService;
import com.chat.chatserver.session.SessionManager;
import com.chat.common.constant.RedisKeys;
import com.chat.common.event.PresenceChangeEvent;
import com.chat.common.util.JsonUtil;
import com.chat.common.ws.WsInboundMessage;
import com.chat.common.ws.WsMessageType;
import com.chat.common.ws.WsOutboundMessage;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final SessionManager sessionManager;
    private final MessageService messageService;
    private final WsMessageCodec codec;
    private final RedisTemplate<String, String> redisTemplate;
    private final ChatMetrics chatMetrics;

    @Value("${chat-server.server-id}")
    private String serverId;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_USERNAME = "username";
    private static final String ATTR_DEVICE_ID = "deviceId";

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        Map<String, String> params = UriComponentsBuilder.fromUri(uri).build()
                .getQueryParams().toSingleValueMap();

        String token = params.get("token");
        String deviceId = params.getOrDefault("deviceId", "default");

        if (token == null || token.isBlank()) {
            log.warn("WebSocket connection rejected: missing token");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        Claims claims;
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.warn("WebSocket connection rejected: invalid token - {}", e.getMessage());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        UUID userId = UUID.fromString(claims.getSubject());
        String username = claims.get("username", String.class);

        session.getAttributes().put(ATTR_USER_ID, userId);
        session.getAttributes().put(ATTR_USERNAME, username);
        session.getAttributes().put(ATTR_DEVICE_ID, deviceId);

        sessionManager.addSession(userId, session);

        // Update Redis: user:devices:{userId} hash -> deviceId -> serverId
        redisTemplate.opsForHash().put(RedisKeys.userDevices(userId), deviceId, serverId);

        // Add to server:users:{serverId} set
        redisTemplate.opsForSet().add(RedisKeys.serverUsers(serverId), userId.toString());

        // Report presence change via Redis pub/sub
        PresenceChangeEvent presenceEvent = PresenceChangeEvent.builder()
                .userId(userId)
                .oldStatus("OFFLINE")
                .newStatus("ONLINE")
                .build();
        redisTemplate.convertAndSend(RedisKeys.PRESENCE_CHANGES, JsonUtil.toJson(presenceEvent));

        log.info("WebSocket connected: user={}, username={}, deviceId={}, sessionId={}",
                userId, username, deviceId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID userId = (UUID) session.getAttributes().get(ATTR_USER_ID);
        String username = (String) session.getAttributes().get(ATTR_USERNAME);

        if (userId == null) {
            log.warn("Received message from unauthenticated session: {}", session.getId());
            return;
        }

        WsInboundMessage inbound;
        try {
            inbound = codec.decode(message.getPayload());
        } catch (Exception e) {
            log.warn("Failed to decode message from user {}: {}", userId, e.getMessage());
            sendError(session, "Invalid message format", null);
            return;
        }

        log.debug("Received {} from user {} (requestId={})", inbound.getType(), userId, inbound.getRequestId());

        switch (inbound.getType()) {
            case SEND_MESSAGE -> {
                chatMetrics.incrementMessagesReceived();
                messageService.handleMessage(
                    userId, username, inbound.getPayload(), inbound.getRequestId(), session);
            }
            case TYPING -> handleTyping(userId, username, inbound.getPayload());
            case HEARTBEAT -> handleHeartbeat(userId);
            case ACK -> log.debug("ACK received from user {} for requestId={}", userId, inbound.getRequestId());
            default -> log.warn("Unknown message type: {} from user {}", inbound.getType(), userId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UUID userId = (UUID) session.getAttributes().get(ATTR_USER_ID);
        String deviceId = (String) session.getAttributes().get(ATTR_DEVICE_ID);

        if (userId == null) {
            return;
        }

        sessionManager.removeSession(userId, session);

        // Remove device from Redis
        if (deviceId != null) {
            redisTemplate.opsForHash().delete(RedisKeys.userDevices(userId), deviceId);
        }

        // Check if user has any remaining devices on any server
        Long deviceCount = redisTemplate.opsForHash().size(RedisKeys.userDevices(userId));
        if (deviceCount == null || deviceCount == 0) {
            // User is fully offline - remove from server set
            redisTemplate.opsForSet().remove(RedisKeys.serverUsers(serverId), userId.toString());

            // Report presence change
            PresenceChangeEvent presenceEvent = PresenceChangeEvent.builder()
                    .userId(userId)
                    .oldStatus("ONLINE")
                    .newStatus("OFFLINE")
                    .build();
            redisTemplate.convertAndSend(RedisKeys.PRESENCE_CHANGES, JsonUtil.toJson(presenceEvent));
        }

        log.info("WebSocket disconnected: user={}, deviceId={}, status={}", userId, deviceId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        UUID userId = (UUID) session.getAttributes().get(ATTR_USER_ID);
        log.error("Transport error for user {}, session {}: {}", userId, session.getId(), exception.getMessage());
        session.close(CloseStatus.SERVER_ERROR);
    }

    private void handleTyping(UUID userId, String username, com.fasterxml.jackson.databind.JsonNode payload) {
        if (payload == null || !payload.has("channelId")) {
            return;
        }
        UUID channelId = UUID.fromString(payload.get("channelId").asText());
        WsOutboundMessage typingMessage = WsOutboundMessage.builder()
                .type(WsMessageType.TYPING)
                .payload(JsonUtil.toJsonNode(Map.of(
                        "channelId", channelId.toString(),
                        "userId", userId.toString(),
                        "username", username
                )))
                .timestamp(Instant.now())
                .build();

        String encoded = codec.encode(typingMessage);
        // Fanout typing indicator to all connected users (simplified: broadcast to all local sessions)
        for (UUID connectedUserId : sessionManager.getAllConnectedUserIds()) {
            if (!connectedUserId.equals(userId)) {
                sessionManager.sendToUser(connectedUserId, encoded);
            }
        }
    }

    private void handleHeartbeat(UUID userId) {
        redisTemplate.opsForValue().set(RedisKeys.heartbeat(userId), String.valueOf(Instant.now().toEpochMilli()));
        log.debug("Heartbeat updated for user {}", userId);
    }

    private void sendError(WebSocketSession session, String errorMessage, String requestId) {
        try {
            WsOutboundMessage.WsOutboundMessageBuilder builder = WsOutboundMessage.builder()
                    .type(WsMessageType.ERROR)
                    .payload(JsonUtil.toJsonNode(Map.of("error", errorMessage)))
                    .timestamp(Instant.now());
            if (requestId != null) {
                builder.requestId(requestId);
            }
            WsOutboundMessage error = builder.build();
            synchronized (session) {
                session.sendMessage(new TextMessage(codec.encode(error)));
            }
        } catch (Exception e) {
            log.error("Failed to send error message to session {}", session.getId(), e);
        }
    }
}
