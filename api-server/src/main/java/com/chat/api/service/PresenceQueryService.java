package com.chat.api.service;

import com.chat.common.constant.RedisKeys;
import com.chat.common.dto.PresenceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceQueryService {

    private final RedisTemplate<String, String> redisTemplate;

    public List<PresenceDto> getPresence(List<UUID> userIds) {
        List<PresenceDto> result = new ArrayList<>();

        for (UUID userId : userIds) {
            String key = RedisKeys.presence(userId);
            String status = redisTemplate.opsForValue().get(key);

            PresenceDto presence = PresenceDto.builder()
                    .userId(userId)
                    .status(status != null ? status : "OFFLINE")
                    .lastSeen(status != null ? Instant.now() : null)
                    .build();

            result.add(presence);
        }

        return result;
    }
}
