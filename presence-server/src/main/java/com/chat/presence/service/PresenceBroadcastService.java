package com.chat.presence.service;

import com.chat.common.constant.RedisKeys;
import com.chat.common.event.PresenceChangeEvent;
import com.chat.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceBroadcastService {

    private final RedisTemplate<String, String> redisTemplate;

    public void broadcastChange(PresenceChangeEvent event) {
        String json = JsonUtil.toJson(event);
        redisTemplate.convertAndSend(RedisKeys.PRESENCE_CHANGES, json);
        log.debug("Broadcast presence change for user {}: {} -> {}",
                event.getUserId(), event.getOldStatus(), event.getNewStatus());
    }
}
