package com.chat.presence.controller;

import com.chat.common.dto.PresenceDto;
import com.chat.presence.service.HeartbeatMonitor;
import com.chat.presence.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/internal/presence")
@RequiredArgsConstructor
public class PresenceInternalController {

    private final PresenceService presenceService;
    private final HeartbeatMonitor heartbeatMonitor;

    @PostMapping("/connect")
    public ResponseEntity<Void> connect(@RequestParam UUID userId) {
        presenceService.handleConnect(userId);
        heartbeatMonitor.trackUser(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/disconnect")
    public ResponseEntity<Void> disconnect(@RequestParam UUID userId) {
        presenceService.handleDisconnect(userId);
        heartbeatMonitor.untrackUser(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestParam UUID userId) {
        presenceService.handleHeartbeat(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<PresenceDto>> getBulkPresence(@RequestParam List<String> userIds) {
        List<UUID> uuids = userIds.stream()
                .map(UUID::fromString)
                .collect(Collectors.toList());
        List<PresenceDto> presences = presenceService.getBulkPresence(uuids);
        return ResponseEntity.ok(presences);
    }
}
