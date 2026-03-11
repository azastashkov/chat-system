package com.chat.loadclient.client;

import com.chat.common.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Slf4j
public class ApiClient {

    private final WebClient webClient;

    public ApiClient(String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public Map<String, String> register(String username, String password, String displayName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("displayName", displayName);

        String response = webClient.post()
                .uri("/api/auth/register")
                .header("Content-Type", "application/json")
                .bodyValue(JsonUtil.toJson(body))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode node = JsonUtil.fromJson(response, JsonNode.class);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("token", node.get("token").asText());
        result.put("userId", node.get("userId").asText());
        return result;
    }

    public Map<String, String> login(String username, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);

        String response = webClient.post()
                .uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .bodyValue(JsonUtil.toJson(body))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode node = JsonUtil.fromJson(response, JsonNode.class);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("token", node.get("token").asText());
        result.put("userId", node.get("userId").asText());
        return result;
    }

    public String createChannel(String token, String type, String name, List<String> memberIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("name", name);
        body.put("memberIds", memberIds);

        String response = webClient.post()
                .uri("/api/channels")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .bodyValue(JsonUtil.toJson(body))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode node = JsonUtil.fromJson(response, JsonNode.class);
        return node.get("channelId").asText();
    }

    public Map<String, String> discoverChatServer(String token) {
        String response = webClient.get()
                .uri("/api/chat/discover")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode node = JsonUtil.fromJson(response, JsonNode.class);
        Map<String, String> result = new LinkedHashMap<>();
        result.put("serverId", node.get("serverId").asText());
        result.put("wsUrl", node.get("wsUrl").asText());
        return result;
    }
}
