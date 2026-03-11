package com.chat.loadclient;

import com.chat.loadclient.scenario.ScenarioRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.cassandra.CassandraReactiveDataAutoConfiguration;

@Slf4j
@SpringBootApplication(exclude = {
        CassandraAutoConfiguration.class,
        CassandraDataAutoConfiguration.class,
        CassandraReactiveDataAutoConfiguration.class
})
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
