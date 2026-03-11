package com.chat.api.controller;

import com.chat.api.service.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/discover")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    @GetMapping("/chat-server")
    public ResponseEntity<Map<String, String>> getChatServer(@RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(discoveryService.getChatServerForUser(userId));
    }
}
