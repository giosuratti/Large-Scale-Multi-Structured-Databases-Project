package it.unipi.findyourdoc.model.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

@Data
@Node("Disease")
public class DiseaseNode {

    @Id
    private String name;

    // Relazione semplice (senza proprietà sull'arco), quindi puntiamo direttamente al Nodo
    @Relationship(type = "TREATED_BY", direction = Relationship.Direction.OUTGOING)
    private List<SpecializationNode> treatedBy;
}