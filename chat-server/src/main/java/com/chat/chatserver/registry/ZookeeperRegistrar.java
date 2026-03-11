package com.chat.chatserver.registry;

import com.chat.common.util.JsonUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ZookeeperRegistrar {

    private final CuratorFramework curatorFramework;

    @Value("${chat-server.server-id}")
    private String serverId;

    @Value("${chat-server.ws-host}")
    private String wsHost;

    @Value("${chat-server.ws-port}")
    private int wsPort;

    @PostConstruct
    public void register() throws Exception {
        String path = "/chat-servers/" + serverId;

        Map<String, Object> nodeData = Map.of(
                "serverId", serverId,
                "host", wsHost,
                "wsPort", wsPort,
                "wsUrl", "ws://" + wsHost + ":" + wsPort + "/ws"
        );

        String json = JsonUtil.toJson(nodeData);

        curatorFramework.create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL)
                .forPath(path, json.getBytes(StandardCharsets.UTF_8));

        log.info("Registered chat server in ZooKeeper: path={}, data={}", path, json);
    }

    @PreDestroy
    public void deregister() {
        log.info("Deregistering chat server {} from ZooKeeper (ephemeral node will auto-delete)", serverId);
    }
}
