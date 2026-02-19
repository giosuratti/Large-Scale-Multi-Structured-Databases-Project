package it.unipi.findyourdoc.model.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

/**
 * Weighted relationship model representing a diagnostic link.
 * Quantifies how strongly a symptom points toward a specific disease.
 */
@Data
@RelationshipProperties
public class IndicatesRelationship {

    /** * Internal Neo4j identifier required for relationship entities with properties. */
    @RelationshipId
    private Long id;

    /** * The clinical relevance or probability factor.
     * Higher weights indicate a stronger correlation between the symptom and the target disease.
     */
    private Double weight;

    /** * The Disease node being pointed to by the symptom. */
    @TargetNode
    private DiseaseNode disease;
}