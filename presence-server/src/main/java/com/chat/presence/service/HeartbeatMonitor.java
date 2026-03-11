package com.chat.presence.service;

import com.chat.common.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatMonitor {

    private final RedisTemplate<String, String> redisTemplate;
    private final PresenceService presenceService;

    private final Set<UUID> knownOnlineUsers = new HashSet<>();

    @Scheduled(fixedRate = 10000)
    public void checkExpiredPresence() {
        log.debug("Checking for expired presence keys, tracking {} users", knownOnlineUsers.size());

        Iterator<UUID> iterator = knownOnlineUsers.iterator();
        while (iterator.hasNext()) {
            UUID userId = iterator.next();
            String presenceKey = RedisKeys.presence(userId);
            String status = redisTemplate.opsForValue().get(presenceKey);

            if (status == null) {
                log.info("Presence key expired for user {}, marking offline", userId);
                presenceService.markOffline(userId);
                iterator.remove();
            }
        }
    }

    public void trackUser(UUID userId) {
        knownOnlineUsers.add(userId);
    }

    public void untrackUser(UUID userId) {
        knownOnlineUsers.remove(userId);
    }

    public Set<UUID> getKnownOnlineUsers() {
        return Set.copyOf(knownOnlineUsers);
    }
}
