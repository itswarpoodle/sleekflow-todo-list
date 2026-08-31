package com.sleekflow.todo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the SleekFlow TODO API and enables the scheduled heartbeat used by
 * the Server-Sent Events connection.
 */
@SpringBootApplication
@EnableScheduling
public class TodoApplication {

    /** Creates the configuration root used by Spring Boot. */
    public TodoApplication() {
    }

    /**
     * Boots the Spring application.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(TodoApplication.class, args);
    }
}
