package com.chat.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceChangeEvent {
    private UUID userId;
    private String oldStatus;
    private String newStatus;
}
