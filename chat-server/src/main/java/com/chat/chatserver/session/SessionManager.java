package com.chat.chatserver.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class SessionManager {

    private final ConcurrentHashMap<UUID, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public void addSession(UUID userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("Session added for user {}. Total sessions: {}", userId, getSessionCount());
    }

    public void removeSession(UUID userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
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
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                } catch (IOException e) {
                    log.error("Failed to send message to session {} for user {}", session.getId(), userId, e);
                }
            }
        }
    }

    public int getSessionCount() {
        return userSessions.values().stream()
                .mapToInt(Set::size)
                .sum();
    }
}
