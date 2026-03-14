package com.chat.chatserver.service;

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
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private HashOperations<String, Object, Object> hashOperations;

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
    void fanout_onlineUsers_publishesToServerChannels() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        ChannelMember member1 = ChannelMember.builder().channelId(channelId).userId(user1).build();
        ChannelMember member2 = ChannelMember.builder().channelId(channelId).userId(user2).build();

        when(cassandraTemplate.select(any(Query.class), eq(ChannelMember.class)))
                .thenReturn(List.of(member1, member2));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        // User1 on server chat-1, User2 on server chat-2
        when(hashOperations.entries("user:devices:" + user1))
                .thenReturn(Map.of("device1", "chat-1"));
        when(hashOperations.entries("user:devices:" + user2))
                .thenReturn(Map.of("device1", "chat-2"));

        messageFanoutService.fanout(event, channelId);

        verify(redisTemplate).convertAndSend(eq("chat:server:chat-1"), anyString());
        verify(redisTemplate).convertAndSend(eq("chat:server:chat-2"), anyString());
    }

    @Test
    void fanout_offlineUsers_publishesNotification() {
        UUID onlineUser = UUID.randomUUID();
        UUID offlineUser = UUID.randomUUID();

        ChannelMember member1 = ChannelMember.builder().channelId(channelId).userId(onlineUser).build();
        ChannelMember member2 = ChannelMember.builder().channelId(channelId).userId(offlineUser).build();

        when(cassandraTemplate.select(any(Query.class), eq(ChannelMember.class)))
                .thenReturn(List.of(member1, member2));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        when(hashOperations.entries("user:devices:" + onlineUser))
                .thenReturn(Map.of("device1", "chat-1"));
        when(hashOperations.entries("user:devices:" + offlineUser))
                .thenReturn(Collections.emptyMap());

        messageFanoutService.fanout(event, channelId);

        // Online user gets message via server channel
        verify(redisTemplate).convertAndSend(eq("chat:server:chat-1"), anyString());
        // Offline user gets notification
        verify(redisTemplate).convertAndSend(eq("notifications"), anyString());
    }

    @Test
    void fanout_multiDeviceUser_groupsByServer() {
        UUID user = UUID.randomUUID();

        ChannelMember member = ChannelMember.builder().channelId(channelId).userId(user).build();

        when(cassandraTemplate.select(any(Query.class), eq(ChannelMember.class)))
                .thenReturn(List.of(member));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        // User has two devices on the same server
        Map<Object, Object> devices = new HashMap<>();
        devices.put("device1", "chat-1");
        devices.put("device2", "chat-1");
        when(hashOperations.entries("user:devices:" + user)).thenReturn(devices);

        messageFanoutService.fanout(event, channelId);

        // Should only publish once to chat-1 (user appears once in the target list)
        verify(redisTemplate, times(1)).convertAndSend(eq("chat:server:chat-1"), anyString());
    }

    @Test
    void fanout_noMembers_doesNotPublish() {
        when(cassandraTemplate.select(any(Query.class), eq(ChannelMember.class)))
                .thenReturn(Collections.emptyList());

        messageFanoutService.fanout(event, channelId);

        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    @Test
    void fanout_usersOnDifferentServers_publishesToEachServer() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        UUID user3 = UUID.randomUUID();

        List<ChannelMember> members = List.of(
                ChannelMember.builder().channelId(channelId).userId(user1).build(),
                ChannelMember.builder().channelId(channelId).userId(user2).build(),
                ChannelMember.builder().channelId(channelId).userId(user3).build()
        );

        when(cassandraTemplate.select(any(Query.class), eq(ChannelMember.class)))
                .thenReturn(members);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        // user1 and user3 on chat-1, user2 on chat-2
        when(hashOperations.entries("user:devices:" + user1))
                .thenReturn(Map.of("device1", "chat-1"));
        when(hashOperations.entries("user:devices:" + user2))
                .thenReturn(Map.of("device1", "chat-2"));
        when(hashOperations.entries("user:devices:" + user3))
                .thenReturn(Map.of("device1", "chat-1"));

        messageFanoutService.fanout(event, channelId);

        verify(redisTemplate).convertAndSend(eq("chat:server:chat-1"), anyString());
        verify(redisTemplate).convertAndSend(eq("chat:server:chat-2"), anyString());
    }
}
