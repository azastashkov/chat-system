package com.chat.chatserver.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class ChannelMemberCache {

    private final Cache<UUID, List<UUID>> cache;

    public ChannelMemberCache() {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(10_000)
                .build();
    }

    public List<UUID> getMembers(UUID channelId) {
        return cache.getIfPresent(channelId);
    }

    public void putMembers(UUID channelId, List<UUID> members) {
        cache.put(channelId, members);
    }

    public void invalidate(UUID channelId) {
        cache.invalidate(channelId);
    }
}
