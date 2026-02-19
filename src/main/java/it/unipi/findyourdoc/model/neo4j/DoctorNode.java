package it.unipi.findyourdoc.model.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

/**
 * Entity representing a Doctor node in the Neo4j graph database.
 * Used for graph-based queries such as finding specialists by proximity or diagnosis.
 */
@Data
@Node("Doctor")
public class DoctorNode {

    /** * Unique identifier for the doctor node, corresponding to the National Provider Identifier. */
    @Id
    private String npi;

    // Property names must match those defined in the Cypher query SET clauses
    private String firstName;
    private String lastName;
    private String city;
    private String phone;

    private Double avgRating;
    private Integer ratingCount;

    /** * Maps the inverse relationship with medical specializations.
     * In Neo4j: (Specialization)-[:HAS_DOCTOR]->(Doctor).
     * Direction.INCOMING is used because the arrow in the graph points towards this node.
     */
    @Relationship(type = "HAS_DOCTOR", direction = Relationship.Direction.INCOMING)
    private SpecializationNode specialization;
}