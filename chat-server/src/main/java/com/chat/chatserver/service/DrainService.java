package com.chat.chatserver.service;

import com.chat.chatserver.codec.WsMessageCodec;
import com.chat.chatserver.registry.ZookeeperRegistrar;
import com.chat.chatserver.session.SessionManager;
import com.chat.common.ws.WsMessageType;
import com.chat.common.ws.WsOutboundMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class DrainService {

    private final ZookeeperRegistrar zookeeperRegistrar;
    private final SessionManager sessionManager;
    private final WsMessageCodec codec;

    private final AtomicBoolean draining = new AtomicBoolean(false);

    public void drain(long timeoutMs) {
        if (!draining.compareAndSet(false, true)) {
            throw new IllegalStateException("Already draining");
        }

        log.info("Starting connection drain with timeout {}ms", timeoutMs);

        // Step 1: Deregister from ZooKeeper
        zookeeperRegistrar.deregisterNow();

        // Step 2: Send RECONNECT to all connected clients
        WsOutboundMessage reconnect = WsOutboundMessage.builder()
                .type(WsMessageType.RECONNECT)
                .timestamp(Instant.now())
                .build();
        String encoded = codec.encode(reconnect);
        for (UUID userId : sessionManager.getAllConnectedUserIds()) {
            sessionManager.sendToUser(userId, encoded);
        }

        // Step 3: Wait for clients to disconnect
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (sessionManager.getSessionCount() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Step 4: Force-close remaining sessions
        int remaining = sessionManager.getSessionCount();
        if (remaining > 0) {
            log.info("Force-closing {} remaining sessions", remaining);
            sessionManager.closeAllSessions();
        }

        log.info("Connection drain complete");
    }

    public boolean isDraining() {
        return draining.get();
    }
}
