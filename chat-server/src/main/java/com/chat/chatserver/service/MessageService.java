package com.chat.chatserver.service;

import com.chat.chatserver.codec.WsMessageCodec;
import com.chat.chatserver.metrics.ChatMetrics;
import com.chat.chatserver.model.ChannelMember;
import com.chat.chatserver.model.Message;
import com.chat.common.constant.RedisKeys;
import com.chat.common.event.ChatMessageEvent;
import com.chat.common.util.JsonUtil;
import com.chat.common.util.TimeUuidUtil;
import com.chat.common.ws.WsMessageType;
import com.chat.common.ws.WsOutboundMessage;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.query.Criteria;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final CassandraTemplate cassandraTemplate;
    private final MessageFanoutService messageFanoutService;
    private final WsMessageCodec codec;
    private final ChatMetrics chatMetrics;
    private final RedisTemplate<String, String> redisTemplate;

    public void handleMessage(UUID senderId, String senderName, JsonNode payload,
                              String requestId, WebSocketSession senderSession) {
        Timer.Sample timer = chatMetrics.startTimer();

        if (payload == null || !payload.has("channelId") || !payload.has("content")) {
            sendError(senderSession, "Missing channelId or content", requestId);
            return;
        }

        UUID channelId = UUID.fromString(payload.get("channelId").asText());
        String content = payload.get("content").asText();
        String messageType = payload.has("messageType") ? payload.get("messageType").asText() : "TEXT";

        // Validate sender is member of channel
        if (!isMemberOfChannel(senderId, channelId)) {
            sendError(senderSession, "Not a member of this channel", requestId);
            return;
        }

        // Generate TimeUUID for message ID
        UUID messageId = TimeUuidUtil.now();
        Instant now = Instant.now();

        // Fix 14: Get sequence number from Redis
        Long sequenceNumber = redisTemplate.opsForValue().increment(RedisKeys.channelSequence(channelId));

        // Build message
        Message message = Message.builder()
                .channelId(channelId)
                .messageId(messageId)
                .senderId(senderId)
                .senderName(senderName)
                .content(content)
                .messageType(messageType)
                .sequenceNumber(sequenceNumber)
                .createdAt(now)
                .build();

        // Fix 6: Persist synchronously - if it fails, send ERROR and return
        try {
            cassandraTemplate.insert(message);
            log.debug("Message {} persisted to Cassandra", messageId);
        } catch (Exception e) {
            log.error("Failed to persist message {} to Cassandra", messageId, e);
            sendError(senderSession, "Failed to persist message", requestId);
            return;
        }

        // Build event for fanout
        ChatMessageEvent event = ChatMessageEvent.builder()
                .messageId(messageId)
                .channelId(channelId)
                .senderId(senderId)
                .senderName(senderName)
                .content(content)
                .messageType(messageType)
                .sequenceNumber(sequenceNumber)
                .timestamp(now)
                .build();

        // Fan out message to channel members
        messageFanoutService.fanout(event, channelId);

        // Send SEND_ACK back to sender
        sendAck(senderSession, messageId, requestId);

        // Fix 6: Metrics after confirmed persistence
        chatMetrics.incrementMessagesSent();
        chatMetrics.recordLatency(timer);

        log.info("Message {} handled: channel={}, sender={}", messageId, channelId, senderId);
    }

    private boolean isMemberOfChannel(UUID userId, UUID channelId) {
        Query query = Query.query(
                Criteria.where("channel_id").is(channelId),
                Criteria.where("user_id").is(userId)
        );
        return cassandraTemplate.exists(query, ChannelMember.class);
    }

    private void sendAck(WebSocketSession session, UUID messageId, String requestId) {
        try {
            WsOutboundMessage ack = WsOutboundMessage.builder()
                    .type(WsMessageType.SEND_ACK)
                    .payload(JsonUtil.toJsonNode(Map.of("messageId", messageId.toString())))
                    .requestId(requestId)
                    .timestamp(Instant.now())
                    .build();
            session.sendMessage(new TextMessage(codec.encode(ack)));
        } catch (Exception e) {
            log.error("Failed to send ACK for requestId={}", requestId, e);
        }
    }

    private void sendError(WebSocketSession session, String errorMessage, String requestId) {
        try {
            WsOutboundMessage error = WsOutboundMessage.builder()
                    .type(WsMessageType.ERROR)
                    .payload(JsonUtil.toJsonNode(Map.of("error", errorMessage)))
                    .requestId(requestId)
                    .timestamp(Instant.now())
                    .build();
            session.sendMessage(new TextMessage(codec.encode(error)));
        } catch (Exception e) {
            log.error("Failed to send error for requestId={}", requestId, e);
        }
    }
}
