package com.chat.chatserver.service;

import com.chat.chatserver.codec.WsMessageCodec;
import com.chat.chatserver.model.ChannelMember;
import com.chat.common.event.ChatMessageEvent;
import com.chat.common.util.JsonUtil;
import com.chat.common.ws.WsOutboundMessage;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private CassandraTemplate cassandraTemplate;

    @Mock
    private MessageFanoutService messageFanoutService;

    @Mock
    private WsMessageCodec codec;

    @Mock
    private WebSocketSession session;

    @InjectMocks
    private MessageService messageService;

    private UUID senderId;
    private String senderName;
    private UUID channelId;

    @BeforeEach
    void setUp() {
        senderId = UUID.randomUUID();
        senderName = "testuser";
        channelId = UUID.randomUUID();
    }

    @Test
    void handleMessage_validMessage_storesAndFansOut() throws Exception {
        JsonNode payload = JsonUtil.toJsonNode(Map.of(
                "channelId", channelId.toString(),
                "content", "Hello, World!"
        ));

        when(cassandraTemplate.exists(any(Query.class), eq(ChannelMember.class))).thenReturn(true);
        when(codec.encode(any(WsOutboundMessage.class))).thenReturn("{\"type\":\"SEND_ACK\"}");

        messageService.handleMessage(senderId, senderName, payload, "req-1", session);

        verify(cassandraTemplate).insert(any(com.chat.chatserver.model.Message.class));
        verify(messageFanoutService).fanout(any(ChatMessageEvent.class), eq(channelId));
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void handleMessage_notMember_sendsError() throws Exception {
        JsonNode payload = JsonUtil.toJsonNode(Map.of(
                "channelId", channelId.toString(),
                "content", "Hello"
        ));

        when(cassandraTemplate.exists(any(Query.class), eq(ChannelMember.class))).thenReturn(false);
        when(codec.encode(any(WsOutboundMessage.class))).thenReturn("{\"type\":\"ERROR\"}");

        messageService.handleMessage(senderId, senderName, payload, "req-1", session);

        verify(cassandraTemplate, never()).insert(any());
        verify(messageFanoutService, never()).fanout(any(), any());
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void handleMessage_missingChannelId_sendsError() throws Exception {
        JsonNode payload = JsonUtil.toJsonNode(Map.of("content", "Hello"));

        when(codec.encode(any(WsOutboundMessage.class))).thenReturn("{\"type\":\"ERROR\"}");

        messageService.handleMessage(senderId, senderName, payload, "req-1", session);

        verify(cassandraTemplate, never()).exists(any(Query.class), any());
        verify(messageFanoutService, never()).fanout(any(), any());
    }

    @Test
    void handleMessage_missingContent_sendsError() throws Exception {
        JsonNode payload = JsonUtil.toJsonNode(Map.of("channelId", channelId.toString()));

        when(codec.encode(any(WsOutboundMessage.class))).thenReturn("{\"type\":\"ERROR\"}");

        messageService.handleMessage(senderId, senderName, payload, "req-1", session);

        verify(cassandraTemplate, never()).exists(any(Query.class), any());
        verify(messageFanoutService, never()).fanout(any(), any());
    }

    @Test
    void handleMessage_nullPayload_sendsError() throws Exception {
        when(codec.encode(any(WsOutboundMessage.class))).thenReturn("{\"type\":\"ERROR\"}");

        messageService.handleMessage(senderId, senderName, null, "req-1", session);

        verify(cassandraTemplate, never()).insert(any());
        verify(messageFanoutService, never()).fanout(any(), any());
    }

    @Test
    void handleMessage_fanoutReceivesCorrectEvent() throws Exception {
        JsonNode payload = JsonUtil.toJsonNode(Map.of(
                "channelId", channelId.toString(),
                "content", "Test content"
        ));

        when(cassandraTemplate.exists(any(Query.class), eq(ChannelMember.class))).thenReturn(true);
        when(codec.encode(any(WsOutboundMessage.class))).thenReturn("{\"type\":\"SEND_ACK\"}");

        messageService.handleMessage(senderId, senderName, payload, "req-1", session);

        ArgumentCaptor<ChatMessageEvent> eventCaptor = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(messageFanoutService).fanout(eventCaptor.capture(), eq(channelId));

        ChatMessageEvent capturedEvent = eventCaptor.getValue();
        assertEquals(channelId, capturedEvent.getChannelId());
        assertEquals(senderId, capturedEvent.getSenderId());
        assertEquals(senderName, capturedEvent.getSenderName());
        assertEquals("Test content", capturedEvent.getContent());
        assertNotNull(capturedEvent.getMessageId());
        assertNotNull(capturedEvent.getTimestamp());
    }
}
