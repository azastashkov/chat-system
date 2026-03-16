package com.chat.chatserver.service;

import com.chat.chatserver.codec.WsMessageCodec;
import com.chat.chatserver.session.SessionManager;
import com.chat.common.event.ChatMessageEvent;
import com.chat.common.util.JsonUtil;
import com.chat.common.ws.WsMessageType;
import com.chat.common.ws.WsOutboundMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageStreamConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final SessionManager sessionManager;
    private final WsMessageCodec codec;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            String json = message.getValue().get("data");
            ChatMessageEvent event = JsonUtil.fromJson(json, ChatMessageEvent.class);

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("messageId", event.getMessageId().toString());
            payloadMap.put("channelId", event.getChannelId().toString());
            payloadMap.put("senderId", event.getSenderId().toString());
            payloadMap.put("senderName", event.getSenderName());
            payloadMap.put("content", event.getContent());
            payloadMap.put("messageType", event.getMessageType());
            payloadMap.put("timestamp", event.getTimestamp().toString());
            if (event.getSequenceNumber() != null) {
                payloadMap.put("sequenceNumber", event.getSequenceNumber());
            }

            WsOutboundMessage outbound = WsOutboundMessage.builder()
                    .type(WsMessageType.MESSAGE_RECEIVED)
                    .payload(JsonUtil.toJsonNode(payloadMap))
                    .timestamp(Instant.now())
                    .build();

            String encoded = codec.encode(outbound);

            if (event.getTargetUserIds() != null) {
                for (UUID targetUserId : event.getTargetUserIds()) {
                    sessionManager.sendToUser(targetUserId, encoded);
                }
            }

            log.debug("Delivered message {} to {} local users from stream",
                    event.getMessageId(),
                    event.getTargetUserIds() != null ? event.getTargetUserIds().size() : 0);
        } catch (Exception e) {
            log.error("Error processing chat message from stream", e);
        }
    }
}
