package com.chat.api.controller;

import com.chat.api.service.ChannelService;
import com.chat.common.dto.ChannelDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @PostMapping
    public ResponseEntity<ChannelDto> createChannel(
            @RequestAttribute("userId") UUID userId,
            @RequestBody CreateChannelRequest request) {
        ChannelDto channel = channelService.createChannel(
                request.channelType(), request.name(), request.memberIds(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(channel);
    }

    @GetMapping
    public ResponseEntity<List<ChannelDto>> getUserChannels(@RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(channelService.getUserChannels(userId));
    }

    @GetMapping("/{channelId}")
    public ResponseEntity<Map<String, Object>> getChannel(@PathVariable UUID channelId) {
        return ResponseEntity.ok(channelService.getChannel(channelId));
    }

    @PostMapping("/{channelId}/members")
    public ResponseEntity<Void> addMembers(
            @PathVariable UUID channelId,
            @RequestBody AddMembersRequest request) {
        channelService.addMembers(channelId, request.userIds());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{channelId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID channelId,
            @PathVariable UUID userId) {
        channelService.removeMember(channelId, userId);
        return ResponseEntity.noContent().build();
    }

    public record CreateChannelRequest(String channelType, String name, List<UUID> memberIds) {}

    public record AddMembersRequest(List<UUID> userIds) {}
}
