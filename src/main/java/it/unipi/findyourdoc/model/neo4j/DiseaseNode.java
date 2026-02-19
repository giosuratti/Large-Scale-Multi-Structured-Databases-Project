package it.unipi.findyourdoc.model.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

/**
 * Graph node representing a medical condition or pathology.
 * Serves as the central entity for mapping symptoms to medical expertise.
 */
@Data
@Node("Disease")
public class DiseaseNode {

    /** * Primary identifier and unique name of the disease (e.g., "Pneumonia"). */
    @Id
    private String name;

    /** * Direct mapping to medical specialties capable of treating this condition.
     * Defined as an outgoing relationship in the graph schema.
     */
    @Relationship(type = "TREATED_BY", direction = Relationship.Direction.OUTGOING)
    private List<SpecializationNode> treatedBy;
}