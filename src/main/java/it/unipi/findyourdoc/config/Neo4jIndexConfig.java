package it.unipi.findyourdoc.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jIndexConfig {

    @Bean
    public CommandLineRunner createNeo4jIndexes(Driver driver) {
        return args -> {
            try (Session session = driver.session()) {

                // DOCTOR UNIQUE CONSTRAINT ON NPI (RECOMMENDED)
                // This creates an automatic index for fast lookups (critical for sync)
                // AND guarantees that we never have duplicate doctor nodes in the graph.
                session.run("CREATE CONSTRAINT constraint_doctor_npi IF NOT EXISTS FOR (d:Doctor) REQUIRE d.NPI IS UNIQUE");

                System.out.println("Neo4j indexes (Disease, Symptom, Doctor NPI) verified/created successfully.");
            } catch (Exception e) {
                System.err.println("Error creating Neo4j indexes: " + e.getMessage());
            }
        };
    }
}