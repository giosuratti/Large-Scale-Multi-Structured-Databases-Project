package it.unipi.findyourdoc.model.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

/**
 * Entity representing a Symptom node in the Neo4j graph database.
 * Symptoms are connected to potential diseases through weighted relationships.
 */
@Data
@Node("Symptom")
public class SymptomNode {

    /** * The primary key is the name of the symptom (e.g., "Fever"). */
    @Id
    private String name;

    /** * Complex relationship representing which diseases this symptom points to.
     * Uses {@link IndicatesRelationship} to capture additional metadata on the edge (like weight).
     */
    @Relationship(type = "INDICATES_DISEASE", direction = Relationship.Direction.OUTGOING)
    private List<IndicatesRelationship> indicatedDiseases;
}