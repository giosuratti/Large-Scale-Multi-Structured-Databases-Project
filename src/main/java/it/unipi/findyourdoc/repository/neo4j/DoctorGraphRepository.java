package it.unipi.findyourdoc.repository.neo4j;

import it.unipi.findyourdoc.dto.neo4j.SpecialistDTO;
import it.unipi.findyourdoc.model.neo4j.DoctorNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface DoctorGraphRepository extends Neo4jRepository<DoctorNode, Long> {

    @Query("MATCH (dis:Disease {name: $diagnosis}) " +
            "MATCH (dis)-[:TREATED_BY]->(spec:Specialization) " +
            "MATCH (spec)-[:HAS_DOCTOR]->(doc:Doctor) " +
            "WHERE toLower(doc.city) = toLower($city) " +

            // MAPPING ESATTO SUI CAMPI DEL DTO
            "RETURN doc.firstName AS firstName," +
            "       doc.lastName AS lastName, " +
            "       spec.name AS specialization, " +
            "       doc.city AS city, " +        // <--- Mappa sul campo 'city' del DTO
            "       coalesce(doc.rating, 0.0) AS rating " +

            "ORDER BY rating DESC")
    List<SpecialistDTO> findSpecialistsByDiagnosisAndCity(
            @Param("city") String city,
            @Param("diagnosis") String diagnosis
    );

    @Query("UNWIND $updates AS row " +
            "MATCH (d:Doctor {npi: row.id}) " + // Usa 'npi' o 'id' in base a come hai mappato la chiave primaria nel Node
            "SET d.rating = row.rating")
    void bulkUpdateRatings(@Param("updates") List<Map<String, Object>> updates);
}