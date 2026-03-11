package com.chat.common.ws;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsOutboundMessage {
    private WsMessageType type;
    private JsonNode payload;
    private String requestId;
    private Instant timestamp;
}
