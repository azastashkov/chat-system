package com.chat.chatserver.handler;

import com.chat.chatserver.codec.WsMessageCodec;
import com.chat.chatserver.service.MessageService;
import com.chat.chatserver.session.SessionManager;
import com.chat.common.util.JsonUtil;
import com.chat.common.ws.WsInboundMessage;
import com.chat.common.ws.WsMessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketHandlerTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private MessageService messageService;

    @Mock
    private WsMessageCodec codec;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private WebSocketSession session;

    @InjectMocks
    private ChatWebSocketHandler handler;

    private static final String SERVER_ID = "chat-1";
    private static final String JWT_SECRET = "myDefaultSecretKeyThatIsLongEnoughForHS256Algorithm123";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "serverId", SERVER_ID);
        ReflectionTestUtils.setField(handler, "jwtSecret", JWT_SECRET);
    }

    @Test
    void afterConnectionEstablished_missingToken_closesSession() throws Exception {
        URI uri = new URI("ws://localhost:8081/ws");
        when(session.getUri()).thenReturn(uri);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.NOT_ACCEPTABLE);
        verify(sessionManager, never()).addSession(any(), any());
    }

    @Test
    void afterConnectionEstablished_nullUri_closesSession() throws Exception {
        when(session.getUri()).thenReturn(null);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.BAD_DATA);
    }

    @Test
    void afterConnectionEstablished_invalidToken_closesSession() throws Exception {
        URI uri = new URI("ws://localhost:8081/ws?token=invalid-token&deviceId=device1");
        when(session.getUri()).thenReturn(uri);

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.NOT_ACCEPTABLE);
        verify(sessionManager, never()).addSession(any(), any());
    }

    @Test
    void afterConnectionClosed_removesSessionAndUpdatesRedis() throws Exception {
        UUID userId = UUID.randomUUID();
        String deviceId = "device1";

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", userId);
        attrs.put("deviceId", deviceId);
        when(session.getAttributes()).thenReturn(attrs);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.size(anyString())).thenReturn(0L);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(sessionManager).removeSession(userId, session);
        verify(hashOperations).delete(eq("user:devices:" + userId), eq(deviceId));
        verify(setOperations).remove(eq("server:users:" + SERVER_ID), eq(userId.toString()));
        verify(redisTemplate).convertAndSend(eq("presence:changes"), anyString());
    }

    @Test
    void afterConnectionClosed_nullUserId_doesNothing() throws Exception {
        Map<String, Object> attrs = new HashMap<>();
        when(session.getAttributes()).thenReturn(attrs);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(sessionManager, never()).removeSession(any(), any());
    }

    @Test
    void handleTextMessage_sendMessage_delegatesToMessageService() throws Exception {
        UUID userId = UUID.randomUUID();
        String username = "testuser";

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", userId);
        attrs.put("username", username);
        when(session.getAttributes()).thenReturn(attrs);

        WsInboundMessage inbound = WsInboundMessage.builder()
                .type(WsMessageType.SEND_MESSAGE)
                .payload(JsonUtil.toJsonNode(Map.of("channelId", UUID.randomUUID().toString(), "content", "hello")))
                .requestId("req-1")
                .build();
        String payload = JsonUtil.toJson(inbound);

        when(codec.decode(payload)).thenReturn(inbound);

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(messageService).handleMessage(eq(userId), eq(username), any(), eq("req-1"), eq(session));
    }

    @Test
    void handleTextMessage_heartbeat_updatesRedis() throws Exception {
        UUID userId = UUID.randomUUID();

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", userId);
        attrs.put("username", "testuser");
        when(session.getAttributes()).thenReturn(attrs);

        WsInboundMessage inbound = WsInboundMessage.builder()
                .type(WsMessageType.HEARTBEAT)
                .requestId("req-hb")
                .build();
        String payload = JsonUtil.toJson(inbound);

        when(codec.decode(payload)).thenReturn(inbound);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(valueOperations).set(eq("heartbeat:" + userId), anyString());
    }

    @Test
    void handleTextMessage_invalidMessage_sendsError() throws Exception {
        UUID userId = UUID.randomUUID();

        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", userId);
        attrs.put("username", "testuser");
        when(session.getAttributes()).thenReturn(attrs);

        String payload = "not-valid-json";
        when(codec.decode(payload)).thenThrow(new RuntimeException("parse error"));

        handler.handleTextMessage(session, new TextMessage(payload));

        verify(messageService, never()).handleMessage(any(), any(), any(), any(), any());
    }
}
