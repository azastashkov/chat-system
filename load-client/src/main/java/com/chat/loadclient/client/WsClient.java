package com.chat.loadclient.client;

import com.chat.common.util.JsonUtil;
import com.chat.common.ws.WsInboundMessage;
import com.chat.common.ws.WsMessageType;
import com.chat.common.ws.WsOutboundMessage;
import com.chat.loadclient.metrics.LoadTestMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WsClient {

    private WebSocketClient webSocketClient;
    private final LoadTestMetrics metrics;
    private final Map<String, Instant> pendingMessages = new ConcurrentHashMap<>();
    private CountDownLatch connectLatch;

    @Getter
    private volatile boolean connected;

    public WsClient(LoadTestMetrics metrics) {
        this.metrics = metrics;
    }

    public boolean connect(String wsUrl, String token, String deviceId) {
        try {
            String fullUrl = wsUrl + "?token=" + token + "&deviceId=" + deviceId;
            connectLatch = new CountDownLatch(1);

            webSocketClient = new WebSocketClient(new URI(fullUrl)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connected = true;
                    metrics.incrementWsConnections();
                    connectLatch.countDown();
                    log.debug("WebSocket connected to {}", wsUrl);
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connected = false;
                    metrics.decrementWsConnections();
                    log.debug("WebSocket closed: {} (remote={})", reason, remote);
                }

                @Override
                public void onError(Exception ex) {
                    metrics.incrementErrors();
                    log.error("WebSocket error", ex);
                }
            };

            webSocketClient.connect();
            return connectLatch.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            metrics.incrementErrors();
            log.error("Failed to connect WebSocket", e);
            return false;
        }
    }

    public void sendMessage(String channelId, String content) {
        try {
            String requestId = UUID.randomUUID().toString();
            Map<String, Object> payload = Map.of(
                    "channelId", channelId,
                    "content", content
            );

            WsInboundMessage msg = WsInboundMessage.builder()
                    .type(WsMessageType.SEND_MESSAGE)
                    .payload(JsonUtil.toJsonNode(payload))
                    .requestId(requestId)
                    .build();

            pendingMessages.put(requestId, Instant.now());
            webSocketClient.send(JsonUtil.toJson(msg));
            metrics.incrementMessagesSent();
        } catch (Exception e) {
            metrics.incrementErrors();
            log.error("Failed to send message", e);
        }
    }

    public void sendHeartbeat() {
        try {
            WsInboundMessage msg = WsInboundMessage.builder()
                    .type(WsMessageType.HEARTBEAT)
                    .requestId(UUID.randomUUID().toString())
                    .build();

            webSocketClient.send(JsonUtil.toJson(msg));
        } catch (Exception e) {
            log.debug("Failed to send heartbeat", e);
        }
    }

    public void close() {
        if (webSocketClient != null) {
            webSocketClient.close();
            connected = false;
        }
    }

    private void handleMessage(String message) {
        try {
            WsOutboundMessage outbound = JsonUtil.fromJson(message, WsOutboundMessage.class);

            if (outbound.getType() == WsMessageType.MESSAGE_RECEIVED) {
                metrics.incrementMessagesReceived();
            }

            if (outbound.getType() == WsMessageType.SEND_ACK && outbound.getRequestId() != null) {
                Instant sentTime = pendingMessages.remove(outbound.getRequestId());
                if (sentTime != null) {
                    long latencyMs = Duration.between(sentTime, Instant.now()).toMillis();
                    metrics.recordLatency(latencyMs);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse WebSocket message", e);
        }
    }
}
