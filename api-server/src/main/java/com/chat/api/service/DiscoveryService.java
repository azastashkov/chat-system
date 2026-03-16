package com.chat.api.service;

import com.chat.api.exception.ApiException;
import com.chat.common.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryService {

    private static final String CHAT_SERVERS_PATH = "/chat-servers";

    private final CuratorFramework curatorFramework;

    public Map<String, String> getChatServerForUser(UUID userId) {
        try {
            List<String> children = curatorFramework.getChildren().forPath(CHAT_SERVERS_PATH);

            if (children == null || children.isEmpty()) {
                throw new ApiException("No chat servers available", HttpStatus.SERVICE_UNAVAILABLE);
            }

            ConsistentHashRing ring = new ConsistentHashRing(new ArrayList<>(children));
            String selectedServer = ring.getServer(userId);

            byte[] data = curatorFramework.getData().forPath(CHAT_SERVERS_PATH + "/" + selectedServer);
            String nodeJson = new String(data, StandardCharsets.UTF_8);
            JsonNode nodeData = JsonUtil.fromJson(nodeJson, JsonNode.class);
            String wsUrl = nodeData.get("wsUrl").asText();

            log.debug("Assigned user {} to chat server {} (wsUrl={})", userId, selectedServer, wsUrl);

            return Map.of(
                    "serverId", selectedServer,
                    "wsUrl", wsUrl
            );
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to discover chat server for user {}", userId, e);
            throw new ApiException("Failed to discover chat server", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }
}
