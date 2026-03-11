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
public class ChannelDto {
    private UUID channelId;
    private String channelType;
    private String name;
    private UUID createdBy;
    private Instant createdAt;
}
