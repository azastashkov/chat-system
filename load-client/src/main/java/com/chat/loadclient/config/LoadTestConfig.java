package com.chat.loadclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "load-test")
public class LoadTestConfig {

    private String apiServerUrl;
    private int numUsers;
    private int messagesPerUser;
    private int rampUpSeconds;
    private int groupSize;
}
