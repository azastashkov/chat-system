package com.chat.api.service;

import com.chat.api.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

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

            List<String> sortedServers = new ArrayList<>(children);
            Collections.sort(sortedServers);

            int hash = Math.abs(userId.hashCode());
            int index = hash % sortedServers.size();
            String selectedServer = sortedServers.get(index);

            byte[] data = curatorFramework.getData().forPath(CHAT_SERVERS_PATH + "/" + selectedServer);
            String wsUrl = new String(data, StandardCharsets.UTF_8);

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
