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
public interface DoctorGraphRepository extends Neo4jRepository<DoctorNode, String> {

    @Query("MATCH (dis:Disease {name: $diagnosis}) " +
            "MATCH (dis)-[:TREATED_BY]->(spec:Specialization) " +
            "MATCH (spec)-[:HAS_DOCTOR]->(doc:Doctor) " +
            "WHERE toLower(doc.city) = toLower($city) " +

            // MAPPING ESATTO SUI CAMPI DEL DTO
            "RETURN doc.NPI AS npi, " +
            "       doc.firstName AS firstName, " +
            "       doc.lastName AS lastName, " +
            "       spec.name AS specialization, " +
            "       doc.city AS city, " + // <--- Mappa sul campo 'city' del DTO
            "       doc.phone AS phone, " +
            "       coalesce(doc.avgRating, 0.0) AS avgRating, " +
            "       doc.ratingCount AS ratingCount " +

            "ORDER BY avgRating DESC")
    List<SpecialistDTO> findSpecialistsByDiagnosisAndCity(
            @Param("city") String city,
            @Param("diagnosis") String diagnosis
    );

    @Query("UNWIND $updates AS row " +
            "MATCH (d:Doctor {NPI: row.npi}) " +
            "SET d.avgRating = row.avgRating, " +
            "d.ratingCount = row.ratingCount")
    void bulkUpdateRatings(@Param("updates") List<Map<String, Object>> updates);

    @Query("MERGE (d:Doctor {id: $id}) " +
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
            @Param("id") String id,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("gender") String gender,
            @Param("city") String city,
            @Param("specialties") List<String> specialties,
            @Param("avgRating") Double avgRating,
            @Param("ratingCount") Integer ratingCount
    );


    @Query("MATCH (d:Doctor {NPI: $npi}) " +
            "SET d.phone = $phone, d.city = $city")
    void updateDoctorDataByNpi(
            @Param("npi") String npi,
            @Param("phone") String phone,
            @Param("city") String city
    );
}