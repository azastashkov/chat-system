package com.chat.loadclient.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class LoadTestMetrics {

    private final Counter messagesSent;
    private final Counter messagesReceived;
    private final Counter errors;
    private final DistributionSummary messageLatency;
    private final AtomicInteger wsConnections;

    public LoadTestMetrics(MeterRegistry meterRegistry) {
        this.messagesSent = Counter.builder("load_test.messages_sent")
                .description("Total messages sent")
                .register(meterRegistry);

        this.messagesReceived = Counter.builder("load_test.messages_received")
                .description("Total messages received")
                .register(meterRegistry);

        this.errors = Counter.builder("load_test.errors")
                .description("Total errors")
                .register(meterRegistry);

        this.messageLatency = DistributionSummary.builder("load_test.message_latency")
                .description("Message round-trip latency in milliseconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        this.wsConnections = new AtomicInteger(0);
        meterRegistry.gauge("load_test.ws_connections", wsConnections);
    }

    public void incrementMessagesSent() {
        messagesSent.increment();
    }

    public void incrementMessagesReceived() {
        messagesReceived.increment();
    }

    public void incrementErrors() {
        errors.increment();
    }

    public void recordLatency(long latencyMs) {
        messageLatency.record(latencyMs);
    }

    public void incrementWsConnections() {
        wsConnections.incrementAndGet();
    }

    public void decrementWsConnections() {
        wsConnections.decrementAndGet();
    }

    public void printSummary() {
        log.info("=== Load Test Summary ===");
        log.info("Messages sent:     {}", (long) messagesSent.count());
        log.info("Messages received: {}", (long) messagesReceived.count());
        log.info("Errors:            {}", (long) errors.count());
        log.info("Latency count:     {}", messageLatency.count());
        log.info("Latency mean:      {} ms", String.format("%.2f", messageLatency.mean()));
        log.info("Latency max:       {} ms", String.format("%.2f", messageLatency.max()));

        var snapshot = messageLatency.takeSnapshot();
        for (var percentile : snapshot.percentileValues()) {
            log.info("Latency p{}:     {} ms",
                    (int) (percentile.percentile() * 100), String.format("%.2f", percentile.value()));
        }
        log.info("WS connections:    {}", wsConnections.get());
        log.info("=========================");
    }
}
