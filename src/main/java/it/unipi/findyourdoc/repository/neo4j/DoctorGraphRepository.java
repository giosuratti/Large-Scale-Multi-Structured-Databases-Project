package it.unipi.findyourdoc.repository.neo4j;

import it.unipi.findyourdoc.dto.neo4j.SpecialistDTO;
import it.unipi.findyourdoc.model.neo4j.DoctorNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Data Access Object for Doctor nodes in Neo4j.
 * Handles complex graph traversals for doctor discovery and high-performance bulk synchronizations.
 */
@Repository
public interface DoctorGraphRepository extends Neo4jRepository<DoctorNode, String> {

    /**
     * Traverses the graph from a diagnosed disease to recommended specialists in a specific city.
     * Maps the resulting nodes and relationships directly into a lightweight DTO.
     */
    @Query("MATCH (dis:Disease {name: $diagnosis}) " +
            "MATCH (dis)-[:TREATED_BY]->(spec:Specialization) " +
            "MATCH (spec)-[:HAS_DOCTOR]->(doc:Doctor) " +
            "WHERE toLower(doc.city) = toLower($city) " +

            // Exact mapping to DTO fields to avoid full node hydration
            "RETURN doc.NPI AS npi, " +
            "       doc.firstName AS firstName, " +
            "       doc.lastName AS lastName, " +
            "       spec.name AS specialization, " +
            "       doc.city AS city, " +
            "       doc.phone AS phone, " +
            "       coalesce(doc.avgRating, 0.0) AS avgRating, " +
            "       doc.ratingCount AS ratingCount " +

            "ORDER BY avgRating DESC")
    List<SpecialistDTO> findSpecialistsByDiagnosisAndCity(
            @Param("city") String city,
            @Param("diagnosis") String diagnosis
    );

    /**
     * Performs a batch update of doctor ratings using Cypher's UNWIND.
     * Processes a list of maps to update multiple nodes in a single transaction.
     */
    @Query("UNWIND $updates AS row " +
            "MATCH (d:Doctor {NPI: row.npi}) " +
            "SET d.avgRating = row.avgRating, " +
            "d.ratingCount = row.ratingCount")
    void bulkUpdateRatings(@Param("updates") List<Map<String, Object>> updates);

    /**
     * Upserts a Doctor node and establishes its relationships with Specialization nodes.
     * Uses MERGE to guarantee idempotency during synchronization operations.
     */
    @Query("MERGE (d:Doctor {NPI: $npi}) " +
            "SET d.firstName = $firstName, " +
            "    d.lastName = $lastName, " +
            "    d.city = $city, " +
            "    d.gender = $gender, " +
            "    d.avgRating = $avgRating, " +
            "    d.ratingCount = $ratingCount, " +
            "WITH d " +
            "UNWIND $specialties AS specName " +
            "MATCH (s:Specialization {name: specName}) " +
            "MERGE (s)-[:HAS_DOCTOR]->(d)")
    void createDoctorAndRelations(
            @Param("npi") String npi,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("gender") String gender,
            @Param("city") String city,
            @Param("specialties") List<String> specialties,
            @Param("avgRating") Double avgRating,
            @Param("ratingCount") Integer ratingCount
    );

    /**
     * Performs a batch update of doctor contact and location details.
     * Utilizes a List of Maps for native, high-performance Neo4j ingestion.
     */
    @Query("UNWIND $updates AS row " +
            "MATCH (d:Doctor {NPI: row.npi}) " +
            "SET d.telephone = row.telephone, d.city = row.city")
    void bulkUpdateDoctors(@Param("updates") List<Map<String, Object>> updates);
}