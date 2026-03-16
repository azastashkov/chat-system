package com.chat.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class ConsistentHashRing {

    private static final int VIRTUAL_NODES = 150;
    private final TreeMap<Integer, String> ring = new TreeMap<>();

    public ConsistentHashRing(List<String> servers) {
        for (String server : servers) {
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                int hash = hash(server + ":" + i);
                ring.put(hash, server);
            }
        }
    }

    public String getServer(UUID userId) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("No servers available");
        }
        int hash = hash(userId.toString());
        Map.Entry<Integer, String> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            entry = ring.firstEntry();
        }
        return entry.getValue();
    }

    private static int hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            return ((digest[0] & 0xFF) << 24) | ((digest[1] & 0xFF) << 16)
                    | ((digest[2] & 0xFF) << 8) | (digest[3] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
