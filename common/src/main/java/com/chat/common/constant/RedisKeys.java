package com.chat.common.constant;

import java.util.UUID;

public final class RedisKeys {

    private RedisKeys() {}

    public static String presence(UUID userId) {
        return "presence:" + userId;
    }

    public static String heartbeat(UUID userId) {
        return "heartbeat:" + userId;
    }

    public static String userServer(UUID userId) {
        return "user:server:" + userId;
    }

    public static String serverUsers(String serverId) {
        return "server:users:" + serverId;
    }

    public static String userDevices(UUID userId) {
        return "user:devices:" + userId;
    }

    public static String chatServerChannel(String serverId) {
        return "chat:server:" + serverId;
    }

    public static String channelSequence(UUID channelId) {
        return "channel:seq:" + channelId;
    }

    public static String chatServerStream(String serverId) {
        return "stream:chat:" + serverId;
    }

    public static String typingChannel(UUID channelId) {
        return "typing:" + channelId;
    }

    public static final String PRESENCE_CHANGES = "presence:changes";
    public static final String NOTIFICATIONS = "notifications";
    public static final String STREAM_NOTIFICATIONS = "stream:notifications";
    public static final String CHANNEL_MEMBERSHIP_INVALIDATE = "channel:membership:invalidate";
}
