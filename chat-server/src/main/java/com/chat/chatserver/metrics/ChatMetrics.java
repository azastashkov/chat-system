package com.chat.chatserver.metrics;

import com.chat.chatserver.session.SessionManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ChatMetrics {

    private final Counter messagesReceived;
    private final Counter messagesSent;
    private final Timer messageLatency;

    public ChatMetrics(MeterRegistry registry, SessionManager sessionManager) {
        registry.gauge("websocket.connections", sessionManager, sm -> sm.getSessionCount());

        this.messagesReceived = Counter.builder("messages.received")
                .description("Total messages received from WebSocket clients")
                .register(registry);

        this.messagesSent = Counter.builder("messages.sent")
                .description("Total messages sent to WebSocket clients")
                .register(registry);

        this.messageLatency = Timer.builder("message.latency")
                .description("Message processing latency")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void incrementMessagesReceived() {
        messagesReceived.increment();
    }

    public void incrementMessagesSent() {
        messagesSent.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void recordLatency(Timer.Sample sample) {
        sample.stop(messageLatency);
    }
}
