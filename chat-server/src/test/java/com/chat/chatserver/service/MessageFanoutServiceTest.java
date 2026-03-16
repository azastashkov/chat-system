package com.chat.chatserver.service;

import com.chat.chatserver.cache.ChannelMemberCache;
import com.chat.chatserver.model.ChannelMember;
import com.chat.common.event.ChatMessageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StreamOperations;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageFanoutServiceTest {

    @Mock
    private CassandraTemplate cassandraTemplate;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ChannelMemberCache channelMemberCache;

    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    @InjectMocks
    private MessageFanoutService messageFanoutService;

    private UUID channelId;
    private ChatMessageEvent event;

    @BeforeEach
    void setUp() {
        channelId = UUID.randomUUID();
        event = ChatMessageEvent.builder()
                .messageId(UUID.randomUUID())
                .channelId(channelId)
                .senderId(UUID.randomUUID())
                .senderName("sender")
                .content("Hello")
                .messageType("TEXT")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fanout_onlineUsers_publishesToServerStreams() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        // Cache miss - query Cassandra
        when(channelMemberCache.getMembers(channelId)).thenReturn(null);
        ChannelMember member1 = ChannelMember.builder().channelId(channelId).userId(user1).build();
        ChannelMember member2 = ChannelMember.builder().channelId(channelId).userId(user2).build();
        when(cassandraTemplate.select(any(Query.class), eq(ChannelMember.class)))
                .thenReturn(List.of(member1, member2));

        // Pipeline returns device maps
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
                .thenReturn(List.of(
                        Map.of("device1", "chat-1"),
                        Map.of("device1", "chat-2")
                ));

        when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        messageFanoutService.fanout(event, channelId);

        // Should publish to streams for both servers
        verify(streamOperations, times(2)).add(any());
        // Cache should be populated
        verify(channelMemberCache).putMembers(eq(channelId), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fanout_cachedMembers_skipsCassandra() {
        UUID user1 = UUID.randomUUID();

        // Cache hit
        when(channelMemberCache.getMembers(channelId)).thenReturn(List.of(user1));

        when(redisTemplate.executePipelined(any(SessionCallback.class)))
                .thenReturn(List.of(Map.of("device1", "chat-1")));

        when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        messageFanoutService.fanout(event, channelId);

        // Should NOT query Cassandra
        verify(cassandraTemplate, never()).select(any(Query.class), eq(ChannelMember.class));
        verify(streamOperations).add(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fanout_offlineUsers_publishesNotificationToStream() {
        UUID onlineUser = UUID.randomUUID();
        UUID offlineUser = UUID.randomUUID();

        when(channelMemberCache.getMembers(channelId)).thenReturn(List.of(onlineUser, offlineUser));

        when(redisTemplate.executePipelined(any(SessionCallback.class)))
                .thenReturn(List.of(
                        Map.of("device1", "chat-1"),
                        Collections.emptyMap()
                ));

        when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        messageFanoutService.fanout(event, channelId);

        // 1 chat stream message + 1 notification stream message = 2
        verify(streamOperations, times(2)).add(any());
    }

    @Test
    void fanout_noMembers_doesNotPublish() {
        when(channelMemberCache.getMembers(channelId)).thenReturn(Collections.emptyList());

        messageFanoutService.fanout(event, channelId);

        verify(redisTemplate, never()).opsForStream();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fanout_multiDeviceUser_groupsByServer() {
        UUID user = UUID.randomUUID();

        when(channelMemberCache.getMembers(channelId)).thenReturn(List.of(user));

        Map<Object, Object> devices = new HashMap<>();
        devices.put("device1", "chat-1");
        devices.put("device2", "chat-1");
        when(redisTemplate.executePipelined(any(SessionCallback.class)))
                .thenReturn(List.of(devices));

        when(redisTemplate.opsForStream()).thenReturn(streamOperations);

        messageFanoutService.fanout(event, channelId);

        // Only 1 stream message (grouped by server)
        verify(streamOperations, times(1)).add(any());
    }
}
