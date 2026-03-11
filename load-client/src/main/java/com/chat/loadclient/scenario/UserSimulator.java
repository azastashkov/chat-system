package com.chat.loadclient.scenario;

import com.chat.loadclient.client.ApiClient;
import com.chat.loadclient.client.WsClient;
import com.chat.loadclient.metrics.LoadTestMetrics;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Data
public class UserSimulator {

    private final String username;
    private final String password;
    private final String displayName;
    private final ApiClient apiClient;
    private final LoadTestMetrics metrics;

    private String userId;
    private String token;
    private WsClient wsClient;
    private final List<String> channelIds = new ArrayList<>();

    private int sentCount;
    private int receivedCount;

    public UserSimulator(String username, ApiClient apiClient, LoadTestMetrics metrics) {
        this.username = username;
        this.password = "password123";
        this.displayName = "User " + username;
        this.apiClient = apiClient;
        this.metrics = metrics;
    }

    public void register() {
        try {
            Map<String, String> result = apiClient.register(username, password, displayName);
            this.token = result.get("token");
            this.userId = result.get("userId");
            log.debug("Registered user {} with id {}", username, userId);
        } catch (Exception e) {
            log.error("Failed to register user {}", username, e);
            metrics.incrementErrors();
        }
    }

    public boolean connect() {
        try {
            Map<String, String> serverInfo = apiClient.discoverChatServer(token);
            String wsUrl = serverInfo.get("wsUrl");
            String deviceId = UUID.randomUUID().toString();

            wsClient = new WsClient(metrics);
            boolean connected = wsClient.connect(wsUrl, token, deviceId);
            if (connected) {
                log.debug("User {} connected to chat server", username);
            } else {
                log.warn("User {} failed to connect to chat server", username);
            }
            return connected;
        } catch (Exception e) {
            log.error("Failed to connect user {}", username, e);
            metrics.incrementErrors();
            return false;
        }
    }

    public void sendMessages(int count) {
        if (wsClient == null || !wsClient.isConnected() || channelIds.isEmpty()) {
            log.warn("User {} cannot send messages: connected={}, channels={}",
                    username, wsClient != null && wsClient.isConnected(), channelIds.size());
            return;
        }

        for (int i = 0; i < count; i++) {
            String channelId = channelIds.get(ThreadLocalRandom.current().nextInt(channelIds.size()));
            String content = String.format("Message %d from %s at %d", i, username, System.currentTimeMillis());
            wsClient.sendMessage(channelId, content);
            sentCount++;

            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(50, 200));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void disconnect() {
        if (wsClient != null) {
            wsClient.close();
            log.debug("User {} disconnected", username);
        }
    }

    public void addChannel(String channelId) {
        channelIds.add(channelId);
    }
}
