package it.unipi.findyourdoc.model.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Data
@Node("Specialization")
public class SpecializationNode {

    @Id
    private String name;

    // NOTA: Non mappiamo la lista dei dottori qui (List<DoctorNode>) per motivi di performance.
    // Se carichi una specializzazione "Cardiologia", non vuoi scaricare 10.000 dottori insieme.
    // La relazione esiste nel DB, ma lato Java la lasciamo "lazy" o la gestiamo dal lato del Dottore (INCOMING).
}