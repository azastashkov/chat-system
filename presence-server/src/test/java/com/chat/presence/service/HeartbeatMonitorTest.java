package com.chat.presence.service;

import com.chat.common.constant.RedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeartbeatMonitorTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private PresenceService presenceService;

    private HeartbeatMonitor heartbeatMonitor;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        heartbeatMonitor = new HeartbeatMonitor(redisTemplate, presenceService);
    }

    @Test
    void checkExpiredPresence_expiredUser_marksOffline() {
        UUID userId = UUID.randomUUID();
        heartbeatMonitor.trackUser(userId);

        when(valueOperations.get(RedisKeys.presence(userId))).thenReturn(null);

        heartbeatMonitor.checkExpiredPresence();

        verify(presenceService).markOffline(userId);
        assertThat(heartbeatMonitor.getKnownOnlineUsers()).doesNotContain(userId);
    }

    @Test
    void checkExpiredPresence_activeUser_doesNotMarkOffline() {
        UUID userId = UUID.randomUUID();
        heartbeatMonitor.trackUser(userId);

        when(valueOperations.get(RedisKeys.presence(userId))).thenReturn("ONLINE");

        heartbeatMonitor.checkExpiredPresence();

        verify(presenceService, never()).markOffline(any());
        assertThat(heartbeatMonitor.getKnownOnlineUsers()).contains(userId);
    }

    @Test
    void trackAndUntrackUser() {
        UUID userId = UUID.randomUUID();

        heartbeatMonitor.trackUser(userId);
        assertThat(heartbeatMonitor.getKnownOnlineUsers()).contains(userId);

        heartbeatMonitor.untrackUser(userId);
        assertThat(heartbeatMonitor.getKnownOnlineUsers()).doesNotContain(userId);
    }
}
