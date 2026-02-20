package it.unipi.findyourdoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main entry point for the FindYourDoc application.
 * Enables Spring Boot autoconfiguration, MongoDB auditing for timestamps, and asynchronous processing.
 */
@SpringBootApplication

@EnableAsync
public class FindYourDocBackendApplication {

    /** * Application execution starts here. */
    public static void main(String[] args) {
        SpringApplication.run(FindYourDocBackendApplication.class, args);
    }
}