package it.unipi.findyourdoc.model.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * Entity representing a Specialization node in the Neo4j graph database.
 * This node acts as a central hub connecting symptoms/diseases to the doctors
 * who specialize in treating them.
 */
@Data
@Node("Specialization")
public class SpecializationNode {

    /** * The unique name of the medical specialization (e.g., "Cardiology"). */
    @Id
    private String name;

    // NOTE: We do not map the list of doctors here (List<DoctorNode>) for performance reasons.
    // Loading a specialization like "Cardiology" would otherwise trigger the retrieval
    // of thousands of associated doctor nodes.
    // The relationship exists in the database, but in Java, we keep it "lazy"
    // or manage it from the Doctor side using INCOMING directions.
}