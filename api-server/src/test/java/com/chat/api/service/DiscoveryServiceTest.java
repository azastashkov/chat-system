package com.chat.api.service;

import com.chat.api.exception.ApiException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.GetChildrenBuilder;
import org.apache.curator.framework.api.GetDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryServiceTest {

    @Mock
    private CuratorFramework curatorFramework;

    @Mock
    private GetChildrenBuilder getChildrenBuilder;

    @Mock
    private GetDataBuilder getDataBuilder;

    @InjectMocks
    private DiscoveryService discoveryService;

    @BeforeEach
    void setUp() throws Exception {
        when(curatorFramework.getChildren()).thenReturn(getChildrenBuilder);
    }

    @Test
    void getChatServerForUser_shouldReturnConsistentServer() throws Exception {
        List<String> servers = List.of("server-1", "server-2", "server-3");
        when(getChildrenBuilder.forPath("/chat-servers")).thenReturn(servers);
        when(curatorFramework.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath(anyString()))
                .thenReturn("ws://server-1:9090".getBytes(StandardCharsets.UTF_8));

        UUID userId = UUID.randomUUID();

        Map<String, String> result1 = discoveryService.getChatServerForUser(userId);
        Map<String, String> result2 = discoveryService.getChatServerForUser(userId);

        assertThat(result1).containsKey("serverId");
        assertThat(result1).containsKey("wsUrl");
        assertThat(result1.get("serverId")).isEqualTo(result2.get("serverId"));
    }

    @Test
    void getChatServerForUser_shouldDistributeAcrossServers() throws Exception {
        List<String> servers = List.of("server-1", "server-2", "server-3");
        when(getChildrenBuilder.forPath("/chat-servers")).thenReturn(servers);
        when(curatorFramework.getData()).thenReturn(getDataBuilder);
        when(getDataBuilder.forPath("/chat-servers/server-1"))
                .thenReturn("ws://server-1:9090".getBytes(StandardCharsets.UTF_8));
        when(getDataBuilder.forPath("/chat-servers/server-2"))
                .thenReturn("ws://server-2:9090".getBytes(StandardCharsets.UTF_8));
        when(getDataBuilder.forPath("/chat-servers/server-3"))
                .thenReturn("ws://server-3:9090".getBytes(StandardCharsets.UTF_8));

        Set<String> assignedServers = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            UUID userId = UUID.randomUUID();
            Map<String, String> result = discoveryService.getChatServerForUser(userId);
            assignedServers.add(result.get("serverId"));
        }

        assertThat(assignedServers.size()).isGreaterThan(1);
    }

    @Test
    void getChatServerForUser_shouldThrowWhenNoServers() throws Exception {
        when(getChildrenBuilder.forPath("/chat-servers")).thenReturn(Collections.emptyList());

        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> discoveryService.getChatServerForUser(userId))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("No chat servers available");
    }
}
