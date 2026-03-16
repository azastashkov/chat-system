package com.chat.chatserver.service;

import com.chat.chatserver.cache.ChannelMemberCache;
import com.chat.chatserver.model.ChannelMember;
import com.chat.common.constant.RedisKeys;
import com.chat.common.event.ChatMessageEvent;
import com.chat.common.event.NotificationEvent;
import com.chat.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.query.Criteria;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
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
    private final ChannelMemberCache channelMemberCache;

    public void fanout(ChatMessageEvent event, UUID channelId) {
        // Fix 10: Use cache for channel members
        List<UUID> memberUserIds = channelMemberCache.getMembers(channelId);
        if (memberUserIds == null) {
            Query query = Query.query(Criteria.where("channel_id").is(channelId));
            List<ChannelMember> members = cassandraTemplate.select(query, ChannelMember.class);
            memberUserIds = members.stream().map(ChannelMember::getUserId).toList();
            channelMemberCache.putMembers(channelId, memberUserIds);
        }

        if (memberUserIds.isEmpty()) {
            return;
        }

        // Fix 11: Pipeline device lookups
        final List<UUID> finalMemberUserIds = memberUserIds;
        List<Object> pipelineResults = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            @SuppressWarnings("unchecked")
            public Object execute(RedisOperations operations) throws DataAccessException {
                for (UUID memberId : finalMemberUserIds) {
                    operations.opsForHash().entries(RedisKeys.userDevices(memberId));
                }
                return null;
            }
        });

        Map<String, List<UUID>> serverToUsers = new HashMap<>();
        List<UUID> offlineUsers = new ArrayList<>();

        for (int i = 0; i < memberUserIds.size(); i++) {
            UUID memberId = memberUserIds.get(i);
            @SuppressWarnings("unchecked")
            Map<Object, Object> devices = (Map<Object, Object>) pipelineResults.get(i);

            if (devices != null && !devices.isEmpty()) {
                for (Object serverIdObj : devices.values()) {
                    serverToUsers.computeIfAbsent(serverIdObj.toString(), k -> new ArrayList<>())
                            .add(memberId);
                }
            } else {
                offlineUsers.add(memberId);
            }
        }

        // Send notifications for offline users via Redis Stream
        for (UUID memberId : offlineUsers) {
            NotificationEvent notification = NotificationEvent.builder()
                    .userId(memberId)
                    .type("NEW_MESSAGE")
                    .payload(JsonUtil.toJson(Map.of(
                            "channelId", channelId.toString(),
                            "senderId", event.getSenderId().toString(),
                            "senderName", event.getSenderName(),
                            "content", truncateContent(event.getContent())
                    )))
                    .timestamp(Instant.now())
                    .build();
            redisTemplate.opsForStream().add(
                    StreamRecords.string(Map.of("data", JsonUtil.toJson(notification)))
                            .withStreamKey(RedisKeys.STREAM_NOTIFICATIONS)
            );
        }

        // Fix 8: Publish to each server's stream instead of pub/sub
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
                    .sequenceNumber(event.getSequenceNumber())
                    .targetUserIds(targetUserIds)
                    .build();

            String streamKey = RedisKeys.chatServerStream(targetServerId);
            redisTemplate.opsForStream().add(
                    StreamRecords.string(Map.of("data", JsonUtil.toJson(serverEvent)))
                            .withStreamKey(streamKey)
            );
            log.debug("Published message {} to stream {} for {} users",
                    event.getMessageId(), streamKey, targetUserIds.size());
        }
    }

    private String truncateContent(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > 100 ? content.substring(0, 100) + "..." : content;
    }
}
