package com.chat.common.ws;

public enum WsMessageType {
    SEND_MESSAGE,
    MESSAGE_RECEIVED,
    SEND_ACK,
    ACK,
    TYPING,
    HEARTBEAT,
    PRESENCE_UPDATE,
    ERROR,
    RECONNECT
}
