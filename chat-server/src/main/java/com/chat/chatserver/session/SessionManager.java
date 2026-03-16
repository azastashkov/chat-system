package com.chat.chatserver.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class SessionManager {

    private final ConcurrentHashMap<UUID, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    @Value("${chat-server.ws-send-time-limit:5000}")
    private int wsSendTimeLimit;

    @Value("${chat-server.ws-send-buffer-size-limit:524288}")
    private int wsSendBufferSizeLimit;

    public void addSession(UUID userId, WebSocketSession session) {
        // Fix 17: Wrap with ConcurrentWebSocketSessionDecorator for backpressure
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(
                session, wsSendTimeLimit, wsSendBufferSizeLimit);
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(decorated);
        log.info("Session added for user {}. Total sessions: {}", userId, getSessionCount());
    }

    public void removeSession(UUID userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.removeIf(s -> {
                if (s instanceof ConcurrentWebSocketSessionDecorator decorator) {
                    return decorator.getDelegate().equals(session);
                }
                return s.equals(session);
            });
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
        log.info("Session removed for user {}. Total sessions: {}", userId, getSessionCount());
    }

    public Set<WebSocketSession> getSessions(UUID userId) {
        return userSessions.getOrDefault(userId, Collections.emptySet());
    }

    public Set<UUID> getAllConnectedUserIds() {
        return Collections.unmodifiableSet(userSessions.keySet());
    }

    public void sendToUser(UUID userId, String message) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("No active sessions for user {}", userId);
            return;
        }
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    // ConcurrentWebSocketSessionDecorator handles concurrency
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    log.error("Failed to send message to session {} for user {}", session.getId(), userId, e);
                }
            }
        }
    }

    public void closeAllSessions() {
        for (Map.Entry<UUID, Set<WebSocketSession>> entry : userSessions.entrySet()) {
            for (WebSocketSession session : entry.getValue()) {
                try {
                    if (session.isOpen()) {
                        session.close(CloseStatus.SERVICE_RESTARTED);
                    }
                } catch (IOException e) {
                    log.warn("Error closing session {} for user {}", session.getId(), entry.getKey(), e);
                }
            }
        }
        userSessions.clear();
    }

    public int getSessionCount() {
        return userSessions.values().stream()
                .mapToInt(Set::size)
                .sum();
    }
}
