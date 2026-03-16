package com.chat.chatserver.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionManagerTest {

    private SessionManager sessionManager;

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    @Mock
    private WebSocketSession session3;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager();
        ReflectionTestUtils.setField(sessionManager, "wsSendTimeLimit", 5000);
        ReflectionTestUtils.setField(sessionManager, "wsSendBufferSizeLimit", 524288);
    }

    @Test
    void addSession_singleDevice() {
        UUID userId = UUID.randomUUID();

        sessionManager.addSession(userId, session1);

        Set<WebSocketSession> sessions = sessionManager.getSessions(userId);
        assertEquals(1, sessions.size());
    }

    @Test
    void addSession_multiDevice() {
        UUID userId = UUID.randomUUID();

        sessionManager.addSession(userId, session1);
        sessionManager.addSession(userId, session2);

        Set<WebSocketSession> sessions = sessionManager.getSessions(userId);
        assertEquals(2, sessions.size());
    }

    @Test
    void removeSession_singleDevice_removesUser() {
        UUID userId = UUID.randomUUID();
        sessionManager.addSession(userId, session1);

        sessionManager.removeSession(userId, session1);

        Set<WebSocketSession> sessions = sessionManager.getSessions(userId);
        assertTrue(sessions.isEmpty());
        assertFalse(sessionManager.getAllConnectedUserIds().contains(userId));
    }

    @Test
    void removeSession_multiDevice_keepsOtherSessions() {
        UUID userId = UUID.randomUUID();
        sessionManager.addSession(userId, session1);
        sessionManager.addSession(userId, session2);

        sessionManager.removeSession(userId, session1);

        Set<WebSocketSession> sessions = sessionManager.getSessions(userId);
        assertEquals(1, sessions.size());
    }

    @Test
    void removeSession_nonExistentUser_doesNotThrow() {
        UUID userId = UUID.randomUUID();
        assertDoesNotThrow(() -> sessionManager.removeSession(userId, session1));
    }

    @Test
    void getAllConnectedUserIds_multipleUsers() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        sessionManager.addSession(userId1, session1);
        sessionManager.addSession(userId2, session2);

        Set<UUID> connectedUserIds = sessionManager.getAllConnectedUserIds();
        assertEquals(2, connectedUserIds.size());
        assertTrue(connectedUserIds.contains(userId1));
        assertTrue(connectedUserIds.contains(userId2));
    }

    @Test
    void sendToUser_sendsToAllSessions() throws Exception {
        UUID userId = UUID.randomUUID();
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);

        sessionManager.addSession(userId, session1);
        sessionManager.addSession(userId, session2);

        sessionManager.sendToUser(userId, "test message");

        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendToUser_skipsClosedSessions() throws Exception {
        UUID userId = UUID.randomUUID();
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(false);

        sessionManager.addSession(userId, session1);
        sessionManager.addSession(userId, session2);

        sessionManager.sendToUser(userId, "test message");

        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendToUser_noSessions_doesNotThrow() {
        UUID userId = UUID.randomUUID();
        assertDoesNotThrow(() -> sessionManager.sendToUser(userId, "test message"));
    }

    @Test
    void getSessionCount_accurate() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        assertEquals(0, sessionManager.getSessionCount());

        sessionManager.addSession(userId1, session1);
        assertEquals(1, sessionManager.getSessionCount());

        sessionManager.addSession(userId1, session2);
        assertEquals(2, sessionManager.getSessionCount());

        sessionManager.addSession(userId2, session3);
        assertEquals(3, sessionManager.getSessionCount());

        sessionManager.removeSession(userId1, session1);
        assertEquals(2, sessionManager.getSessionCount());
    }

    @Test
    void getSessions_unknownUser_returnsEmptySet() {
        UUID unknownUserId = UUID.randomUUID();
        Set<WebSocketSession> sessions = sessionManager.getSessions(unknownUserId);
        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void closeAllSessions_closesAndClearsAll() throws Exception {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);

        sessionManager.addSession(userId1, session1);
        sessionManager.addSession(userId2, session2);

        sessionManager.closeAllSessions();

        assertEquals(0, sessionManager.getSessionCount());
        verify(session1).close(any());
        verify(session2).close(any());
    }
}
