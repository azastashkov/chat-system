package com.chat.chatserver.service;

import com.chat.chatserver.model.ChannelMember;
import com.chat.common.constant.RedisKeys;
import com.chat.common.event.ChatMessageEvent;
import com.chat.common.event.NotificationEvent;
import com.chat.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.query.Criteria;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageFanoutService {

    private final CassandraTemplate cassandraTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    public void fanout(ChatMessageEvent event, UUID channelId) {
        // Get all channel members
        Query query = Query.query(Criteria.where("channel_id").is(channelId));
        List<ChannelMember> members = cassandraTemplate.select(query, ChannelMember.class);

        // Group members by their server assignment
        Map<String, List<UUID>> serverToUsers = new HashMap<>();

        for (ChannelMember member : members) {
            UUID memberUserId = member.getUserId();

            // Check Redis for user's devices
            Map<Object, Object> devices = redisTemplate.opsForHash()
                    .entries(RedisKeys.userDevices(memberUserId));

            if (devices != null && !devices.isEmpty()) {
                // User is online - group by serverId
                for (Object serverIdObj : devices.values()) {
                    String targetServerId = serverIdObj.toString();
                    serverToUsers.computeIfAbsent(targetServerId, k -> new java.util.ArrayList<>())
                            .add(memberUserId);
                }
            } else {
                // User is offline - send notification
                NotificationEvent notification = NotificationEvent.builder()
                        .userId(memberUserId)
                        .type("NEW_MESSAGE")
                        .payload(JsonUtil.toJson(Map.of(
                                "channelId", channelId.toString(),
                                "senderId", event.getSenderId().toString(),
                                "senderName", event.getSenderName(),
                                "content", truncateContent(event.getContent())
                        )))
                        .timestamp(Instant.now())
                        .build();
                redisTemplate.convertAndSend(RedisKeys.NOTIFICATIONS, JsonUtil.toJson(notification));
            }
        }

        // Publish ChatMessageEvent to each server's channel
        for (Map.Entry<String, List<UUID>> entry : serverToUsers.entrySet()) {
            String targetServerId = entry.getKey();
            List<UUID> targetUserIds = entry.getValue();

            ChatMessageEvent serverEvent = ChatMessageEvent.builder()
                    .messageId(event.getMessageId())
                    .channelId(event.getChannelId())
                    .senderId(event.getSenderId())
                    .senderName(event.getSenderName())
                    .content(event.getContent())
                    .messageType(event.getMessageType())
                    .timestamp(event.getTimestamp())
                    .targetUserIds(targetUserIds)
                    .build();

            String channel = RedisKeys.chatServerChannel(targetServerId);
            redisTemplate.convertAndSend(channel, JsonUtil.toJson(serverEvent));
            log.debug("Published message {} to server {} for {} users",
                    event.getMessageId(), targetServerId, targetUserIds.size());
        }
    }

    private String truncateContent(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > 100 ? content.substring(0, 100) + "..." : content;
    }
}
