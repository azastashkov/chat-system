package com.chat.api.controller;

import com.chat.api.service.PresenceQueryService;
import com.chat.common.dto.PresenceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceQueryService presenceQueryService;

    @GetMapping
    public ResponseEntity<List<PresenceDto>> getPresence(@RequestParam("userIds") String userIds) {
        List<UUID> ids = Arrays.stream(userIds.split(","))
                .map(String::trim)
                .map(UUID::fromString)
                .toList();

        return ResponseEntity.ok(presenceQueryService.getPresence(ids));
    }
}
