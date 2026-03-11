package com.chat.presence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PresenceServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PresenceServerApplication.class, args);
    }
}
