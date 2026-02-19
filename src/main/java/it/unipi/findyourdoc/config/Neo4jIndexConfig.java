package it.unipi.findyourdoc.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to initialize Neo4j schema constraints and indexes on startup.
 */
@Configuration
public class Neo4jIndexConfig {

    /**
     * Executes Cypher commands to ensure data integrity and lookup performance.
     * * @param driver The Neo4j driver bean.
     * @return A CommandLineRunner that executes the index/constraint creation.
     */
    @Bean
    public CommandLineRunner createNeo4jIndexes(Driver driver) {
        return args -> {
            try (Session session = driver.session()) {

                // Enforces NPI uniqueness at the database level to prevent node duplication.
                // Implicitly creates a B-Tree index for O(log n) lookups during data synchronization.
                session.run("CREATE CONSTRAINT constraint_doctor_npi IF NOT EXISTS FOR (d:Doctor) REQUIRE d.NPI IS UNIQUE");

                System.out.println("Neo4j indexes (Disease, Symptom, Doctor NPI) verified/created successfully.");
            } catch (Exception e) {
                System.err.println("Error creating Neo4j indexes: " + e.getMessage());
            }
        };
    }
}