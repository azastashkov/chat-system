package com.chat.presence.service;

import com.chat.common.constant.RedisKeys;
import com.chat.common.dto.PresenceDto;
import com.chat.common.event.PresenceChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private static final Duration PRESENCE_TTL = Duration.ofSeconds(60);
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(60);
    private static final String ONLINE = "ONLINE";
    private static final String OFFLINE = "OFFLINE";

    private final RedisTemplate<String, String> redisTemplate;
    private final PresenceBroadcastService broadcastService;

    // Fix 16: Chat-server is now the authority for setting presence.
    // Presence-server only acts as a safety net for TTL-based cleanup.
    public void handleConnect(UUID userId) {
        log.info("User {} connected (presence managed by chat-server)", userId);
    }

    public void handleDisconnect(UUID userId) {
        log.info("User {} disconnected (presence managed by chat-server)", userId);
    }

    public void handleHeartbeat(UUID userId) {
        String presenceKey = RedisKeys.presence(userId);
        redisTemplate.expire(presenceKey, PRESENCE_TTL);

        String heartbeatKey = RedisKeys.heartbeat(userId);
        redisTemplate.opsForValue().set(heartbeatKey, Instant.now().toString(), HEARTBEAT_TTL);

        log.debug("Heartbeat received for user {}", userId);
    }

    public PresenceDto getPresence(UUID userId) {
        String key = RedisKeys.presence(userId);
        String status = redisTemplate.opsForValue().get(key);

        return PresenceDto.builder()
                .userId(userId)
                .status(status != null ? status : OFFLINE)
                .lastSeen(Instant.now())
                .build();
    }

    public List<PresenceDto> getBulkPresence(List<UUID> userIds) {
        List<String> keys = userIds.stream()
                .map(RedisKeys::presence)
                .collect(Collectors.toList());

        List<String> statuses = redisTemplate.opsForValue().multiGet(keys);

        return java.util.stream.IntStream.range(0, userIds.size())
                .mapToObj(i -> PresenceDto.builder()
                        .userId(userIds.get(i))
                        .status(statuses != null && statuses.get(i) != null ? statuses.get(i) : OFFLINE)
                        .lastSeen(Instant.now())
                        .build())
                .collect(Collectors.toList());
    }

    // Safety net: called when presence TTL expires without heartbeat renewal
    public void markOffline(UUID userId) {
        String key = RedisKeys.presence(userId);
        String currentStatus = redisTemplate.opsForValue().get(key);

        if (currentStatus == null || OFFLINE.equals(currentStatus)) {
            return;
        }

        redisTemplate.opsForValue().set(key, OFFLINE);
        log.info("User {} presence expired, marking OFFLINE", userId);

        PresenceChangeEvent event = PresenceChangeEvent.builder()
                .userId(userId)
                .oldStatus(ONLINE)
                .newStatus(OFFLINE)
                .build();
        broadcastService.broadcastChange(event);
    }
}
