package com.chat.loadclient;

import com.chat.loadclient.scenario.ScenarioRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
@RequiredArgsConstructor
public class LoadClientApplication implements CommandLineRunner {

    private final ScenarioRunner scenarioRunner;

    public static void main(String[] args) {
        SpringApplication.run(LoadClientApplication.class, args);
    }

    @Override
    public void run(String... args) {
        log.info("Starting load test...");
        scenarioRunner.run();
        log.info("Load test completed.");
    }
}
