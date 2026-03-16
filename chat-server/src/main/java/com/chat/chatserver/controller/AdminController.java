package com.chat.chatserver.controller;

import com.chat.chatserver.service.DrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DrainService drainService;

    @PostMapping("/drain")
    public ResponseEntity<String> drain(@RequestParam(defaultValue = "30000") long timeoutMs) {
        drainService.drain(timeoutMs);
        return ResponseEntity.ok("Drain complete");
    }
}
