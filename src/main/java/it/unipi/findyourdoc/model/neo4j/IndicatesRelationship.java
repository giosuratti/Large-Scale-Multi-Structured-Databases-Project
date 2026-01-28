package it.unipi.findyourdoc.model.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@Data
@RelationshipProperties
public class IndicatesRelationship {

    @RelationshipId
    private Long id; // ID interno obbligatorio per le RelationshipProperties in SDN

    private Double weight; // La proprietà "weight" caricata dal CSV

    @TargetNode
    private DiseaseNode disease; // Il nodo di destinazione
}