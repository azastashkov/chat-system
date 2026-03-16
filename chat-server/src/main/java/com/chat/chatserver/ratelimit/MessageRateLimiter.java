package com.chat.chatserver.ratelimit;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MessageRateLimiter {

    private static final int MAX_MESSAGES_PER_SECOND = 30;
    private static final long WINDOW_MS = 1000;

    // [0] = window start timestamp, [1] = message count
    private final ConcurrentHashMap<UUID, AtomicLong[]> userBuckets = new ConcurrentHashMap<>();

    public boolean allowMessage(UUID userId) {
        long now = System.currentTimeMillis();
        AtomicLong[] bucket = userBuckets.computeIfAbsent(userId, k -> new AtomicLong[]{
                new AtomicLong(now), new AtomicLong(0)
        });

        long windowStart = bucket[0].get();
        if (now - windowStart >= WINDOW_MS) {
            bucket[0].set(now);
            bucket[1].set(1);
            return true;
        }

        long count = bucket[1].incrementAndGet();
        return count <= MAX_MESSAGES_PER_SECOND;
    }
}
