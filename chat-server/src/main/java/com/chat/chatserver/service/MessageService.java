package com.chat.chatserver.service;

import com.chat.chatserver.codec.WsMessageCodec;
import com.chat.chatserver.model.ChannelMember;
import com.chat.chatserver.model.Message;
import com.chat.common.event.ChatMessageEvent;
import com.chat.common.util.JsonUtil;
import com.chat.common.util.TimeUuidUtil;
import com.chat.common.ws.WsMessageType;
import com.chat.common.ws.WsOutboundMessage;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.query.Criteria;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.scheduling.annotation.Async;
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

    public void handleMessage(UUID senderId, String senderName, JsonNode payload,
                              String requestId, WebSocketSession senderSession) {
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

        // Store message in Cassandra
        Message message = Message.builder()
                .channelId(channelId)
                .messageId(messageId)
                .senderId(senderId)
                .senderName(senderName)
                .content(content)
                .messageType(messageType)
                .createdAt(now)
                .build();

        persistMessageAsync(message);

        // Build event for fanout
        ChatMessageEvent event = ChatMessageEvent.builder()
                .messageId(messageId)
                .channelId(channelId)
                .senderId(senderId)
                .senderName(senderName)
                .content(content)
                .messageType(messageType)
                .timestamp(now)
                .build();

        // Fan out message to channel members
        messageFanoutService.fanout(event, channelId);

        // Send SEND_ACK back to sender
        sendAck(senderSession, messageId, requestId);

        log.info("Message {} handled: channel={}, sender={}", messageId, channelId, senderId);
    }

    private boolean isMemberOfChannel(UUID userId, UUID channelId) {
        Query query = Query.query(
                Criteria.where("channel_id").is(channelId),
                Criteria.where("user_id").is(userId)
        );
        return cassandraTemplate.exists(query, ChannelMember.class);
    }

    @Async
    public void persistMessageAsync(Message message) {
        try {
            cassandraTemplate.insert(message);
            log.debug("Message {} persisted to Cassandra", message.getMessageId());
        } catch (Exception e) {
            log.error("Failed to persist message {} to Cassandra", message.getMessageId(), e);
        }
    }

    private void sendAck(WebSocketSession session, UUID messageId, String requestId) {
        try {
            WsOutboundMessage ack = WsOutboundMessage.builder()
                    .type(WsMessageType.SEND_ACK)
                    .payload(JsonUtil.toJsonNode(Map.of("messageId", messageId.toString())))
                    .requestId(requestId)
                    .timestamp(Instant.now())
                    .build();
            synchronized (session) {
                session.sendMessage(new TextMessage(codec.encode(ack)));
            }
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
            synchronized (session) {
                session.sendMessage(new TextMessage(codec.encode(error)));
            }
        } catch (Exception e) {
            log.error("Failed to send error for requestId={}", requestId, e);
        }
    }
}
