package it.unipi.findyourdoc.model.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

@Data
@Node("Doctor")
public class DoctorNode {

    @Id
    private String npi; // L'ID univoco caricato dal CSV (NPI)

    // I nomi delle proprietà devono coincidere con quelli nel SET della tua query Cypher
    private String firstName;
    private String lastName;
    private String city;
    private String phone;

    private Double AverageRating;

    // Mappiamo la relazione inversa per comodità.
    // Su Neo4j: (Spec)-[:HAS_DOCTOR]->(Doc)
    // Qui diciamo: Il dottore "ha" una specializzazione che gli "entra" (INCOMING).
    @Relationship(type = "HAS_DOCTOR", direction = Relationship.Direction.INCOMING)
    private SpecializationNode specialization;
}