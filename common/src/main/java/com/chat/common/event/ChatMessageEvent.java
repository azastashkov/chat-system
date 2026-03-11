package com.chat.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEvent {
    private UUID messageId;
    private UUID channelId;
    private UUID senderId;
    private String senderName;
    private String content;
    private String messageType;
    private Instant timestamp;
    private List<UUID> targetUserIds;
}
