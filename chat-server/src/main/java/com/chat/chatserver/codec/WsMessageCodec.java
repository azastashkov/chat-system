package com.chat.chatserver.codec;

import com.chat.common.util.JsonUtil;
import com.chat.common.ws.WsInboundMessage;
import com.chat.common.ws.WsOutboundMessage;
import org.springframework.stereotype.Component;

@Component
public class WsMessageCodec {

    public String encode(WsOutboundMessage message) {
        return JsonUtil.toJson(message);
    }

    public WsInboundMessage decode(String text) {
        return JsonUtil.fromJson(text, WsInboundMessage.class);
    }
}
