package com.chat.api.controller;

import com.chat.api.service.MessageService;
import com.chat.common.dto.MessageDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/channels/{channelId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ResponseEntity<List<MessageDto>> getMessages(
            @PathVariable UUID channelId,
            @RequestParam(value = "before", required = false) UUID before,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(messageService.getMessages(channelId, before, limit));
    }
}
