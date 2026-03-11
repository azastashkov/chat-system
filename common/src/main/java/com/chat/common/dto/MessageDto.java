package com.chat.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDto {
    private UUID messageId;
    private UUID channelId;
    private UUID senderId;
    private String senderName;
    private String content;
    private String messageType;
    private Instant createdAt;
}
