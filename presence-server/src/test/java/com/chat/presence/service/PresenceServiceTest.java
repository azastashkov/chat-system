package com.chat.presence.service;

import com.chat.common.constant.RedisKeys;
import com.chat.common.dto.PresenceDto;
import com.chat.common.event.PresenceChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private PresenceBroadcastService broadcastService;

    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        presenceService = new PresenceService(redisTemplate, broadcastService);
    }

    @Test
    void handleConnect_newUser_setsOnlineAndBroadcasts() {
        UUID userId = UUID.randomUUID();
        String key = RedisKeys.presence(userId);
        when(valueOperations.get(key)).thenReturn(null);

        presenceService.handleConnect(userId);

        verify(valueOperations).set(eq(key), eq("ONLINE"), eq(Duration.ofSeconds(90)));

        ArgumentCaptor<PresenceChangeEvent> captor = ArgumentCaptor.forClass(PresenceChangeEvent.class);
        verify(broadcastService).broadcastChange(captor.capture());
        PresenceChangeEvent event = captor.getValue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getOldStatus()).isEqualTo("OFFLINE");
        assertThat(event.getNewStatus()).isEqualTo("ONLINE");
    }

    @Test
    void handleConnect_alreadyOnline_doesNotBroadcast() {
        UUID userId = UUID.randomUUID();
        String key = RedisKeys.presence(userId);
        when(valueOperations.get(key)).thenReturn("ONLINE");

        presenceService.handleConnect(userId);

        verify(valueOperations).set(eq(key), eq("ONLINE"), eq(Duration.ofSeconds(90)));
        verify(broadcastService, never()).broadcastChange(any());
    }

    @Test
    void handleDisconnect_setsOfflineAndBroadcasts() {
        UUID userId = UUID.randomUUID();
        String key = RedisKeys.presence(userId);

        presenceService.handleDisconnect(userId);

        verify(valueOperations).set(key, "OFFLINE");

        ArgumentCaptor<PresenceChangeEvent> captor = ArgumentCaptor.forClass(PresenceChangeEvent.class);
        verify(broadcastService).broadcastChange(captor.capture());
        PresenceChangeEvent event = captor.getValue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getOldStatus()).isEqualTo("ONLINE");
        assertThat(event.getNewStatus()).isEqualTo("OFFLINE");
    }

    @Test
    void handleHeartbeat_refreshesTtl() {
        UUID userId = UUID.randomUUID();
        String presenceKey = RedisKeys.presence(userId);
        String heartbeatKey = RedisKeys.heartbeat(userId);

        presenceService.handleHeartbeat(userId);

        verify(redisTemplate).expire(eq(presenceKey), eq(Duration.ofSeconds(90)));
        verify(valueOperations).set(eq(heartbeatKey), anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    void getPresence_existingUser_returnsStatus() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.get(RedisKeys.presence(userId))).thenReturn("ONLINE");

        PresenceDto dto = presenceService.getPresence(userId);

        assertThat(dto.getUserId()).isEqualTo(userId);
        assertThat(dto.getStatus()).isEqualTo("ONLINE");
        assertThat(dto.getLastSeen()).isNotNull();
    }

    @Test
    void getPresence_unknownUser_returnsOffline() {
        UUID userId = UUID.randomUUID();
        when(valueOperations.get(RedisKeys.presence(userId))).thenReturn(null);

        PresenceDto dto = presenceService.getPresence(userId);

        assertThat(dto.getStatus()).isEqualTo("OFFLINE");
    }

    @Test
    void getBulkPresence_returnsMixedStatuses() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        List<UUID> userIds = Arrays.asList(user1, user2);
        List<String> keys = Arrays.asList(RedisKeys.presence(user1), RedisKeys.presence(user2));

        when(valueOperations.multiGet(keys)).thenReturn(Arrays.asList("ONLINE", null));

        List<PresenceDto> result = presenceService.getBulkPresence(userIds);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatus()).isEqualTo("ONLINE");
        assertThat(result.get(1).getStatus()).isEqualTo("OFFLINE");
    }
}
