package it.unipi.findyourdoc.model.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.List;

@Data
@Node("Symptom")
public class SymptomNode {

    @Id
    private String name; // La chiave primaria è il nome del sintomo

    // Relazione complessa con proprietà (peso)
    @Relationship(type = "INDICATES_DISEASE", direction = Relationship.Direction.OUTGOING)
    private List<IndicatesRelationship> indicatedDiseases;
}