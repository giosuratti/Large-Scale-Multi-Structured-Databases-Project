package it.unipi.findyourdoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Main entry point for the FindYourDoc application.
 * * The @EnableMongoAuditing annotation is required to automatically
 * handle the @CreatedDate field in your User model.
 */
@SpringBootApplication
@EnableMongoAuditing
@EnableAsync
public class FindYourDocBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(FindYourDocBackendApplication.class, args);
    }
}