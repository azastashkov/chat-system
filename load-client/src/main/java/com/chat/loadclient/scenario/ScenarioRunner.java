package com.chat.loadclient.scenario;

import com.chat.loadclient.client.ApiClient;
import com.chat.loadclient.config.LoadTestConfig;
import com.chat.loadclient.metrics.LoadTestMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScenarioRunner {

    private final LoadTestConfig config;
    private final LoadTestMetrics metrics;

    public void run() {
        log.info("Load test configuration: {} users, {} messages/user, {} ramp-up seconds, group size {}",
                config.getNumUsers(), config.getMessagesPerUser(), config.getRampUpSeconds(), config.getGroupSize());

        ApiClient apiClient = new ApiClient(config.getApiServerUrl());
        List<UserSimulator> users = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(config.getNumUsers(), Runtime.getRuntime().availableProcessors() * 2));

        try {
            // Step 1: Register users
            log.info("Step 1: Registering {} users...", config.getNumUsers());
            for (int i = 0; i < config.getNumUsers(); i++) {
                String username = "loaduser_" + System.currentTimeMillis() + "_" + i;
                UserSimulator user = new UserSimulator(username, apiClient, metrics);
                user.register();
                users.add(user);
            }
            log.info("Registered {} users successfully", users.stream().filter(u -> u.getUserId() != null).count());

            // Filter out failed registrations
            users.removeIf(u -> u.getUserId() == null || u.getToken() == null);

            if (users.isEmpty()) {
                log.error("No users registered successfully, aborting load test");
                return;
            }

            // Step 2: Create direct channels between random pairs
            log.info("Step 2: Creating direct channels...");
            List<UserSimulator> shuffled = new ArrayList<>(users);
            Collections.shuffle(shuffled);
            for (int i = 0; i + 1 < shuffled.size(); i += 2) {
                UserSimulator u1 = shuffled.get(i);
                UserSimulator u2 = shuffled.get(i + 1);
                try {
                    String channelId = apiClient.createChannel(
                            u1.getToken(), "DIRECT", null, List.of(u1.getUserId(), u2.getUserId()));
                    u1.addChannel(channelId);
                    u2.addChannel(channelId);
                } catch (Exception e) {
                    log.warn("Failed to create direct channel between {} and {}", u1.getUsername(), u2.getUsername(), e);
                }
            }

            // Step 3: Create group channels
            log.info("Step 3: Creating group channels...");
            int groupSize = config.getGroupSize();
            for (int i = 0; i + groupSize <= users.size(); i += groupSize) {
                List<UserSimulator> groupMembers = users.subList(i, i + groupSize);
                List<String> memberIds = groupMembers.stream()
                        .map(UserSimulator::getUserId)
                        .toList();
                try {
                    String channelId = apiClient.createChannel(
                            groupMembers.get(0).getToken(), "GROUP",
                            "Group_" + i, memberIds);
                    groupMembers.forEach(u -> u.addChannel(channelId));
                } catch (Exception e) {
                    log.warn("Failed to create group channel at index {}", i, e);
                }
            }

            // Step 4: Connect all users via WebSocket with ramp-up
            log.info("Step 4: Connecting {} users over {} seconds...", users.size(), config.getRampUpSeconds());
            long rampUpDelayMs = (config.getRampUpSeconds() * 1000L) / Math.max(users.size(), 1);
            for (UserSimulator user : users) {
                user.connect();
                if (rampUpDelayMs > 0) {
                    Thread.sleep(rampUpDelayMs);
                }
            }
            long connectedCount = users.stream().filter(u -> u.getWsClient() != null && u.getWsClient().isConnected()).count();
            log.info("Connected {} users successfully", connectedCount);

            // Step 5: Send messages
            log.info("Step 5: Each user sending {} messages...", config.getMessagesPerUser());
            List<Future<?>> futures = new ArrayList<>();
            for (UserSimulator user : users) {
                futures.add(executor.submit(() -> user.sendMessages(config.getMessagesPerUser())));
            }

            // Step 6: Wait for completion
            log.info("Step 6: Waiting for all messages to be sent...");
            for (Future<?> future : futures) {
                try {
                    future.get(5, TimeUnit.MINUTES);
                } catch (TimeoutException e) {
                    log.warn("Message sending timed out for a user");
                } catch (ExecutionException e) {
                    log.warn("Error during message sending", e.getCause());
                }
            }

            // Wait a bit for remaining deliveries
            log.info("Waiting for message delivery...");
            Thread.sleep(5000);

            // Step 7: Print summary
            metrics.printSummary();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Load test interrupted", e);
        } catch (Exception e) {
            log.error("Load test failed", e);
        } finally {
            // Step 8: Disconnect all users
            log.info("Step 8: Disconnecting all users...");
            for (UserSimulator user : users) {
                user.disconnect();
            }
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Load test cleanup complete");
        }
    }
}
