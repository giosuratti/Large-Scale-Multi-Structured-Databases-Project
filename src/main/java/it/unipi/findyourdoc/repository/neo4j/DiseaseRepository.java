package it.unipi.findyourdoc.repository.neo4j;

import it.unipi.findyourdoc.model.neo4j.DiseaseNode; // Assumo tu abbia un nodo Disease
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DiseaseRepository extends Neo4jRepository<DiseaseNode, String> {

    /**
     * Trova le diagnosi più probabili basandosi su una lista di sintomi.
     * Logica: Conta quanti sintomi in comune ha una malattia con la lista fornita
     * e ordina per numero di corrispondenze decrescente.
     */
    @Query("MATCH (s:Symptom)-[:INDICATES]->(d:Disease) " +
            "WHERE s.name IN $symptoms " +
            "WITH d, count(s) AS matches " +
            "ORDER BY matches DESC " +
            "LIMIT 5 " +
            "RETURN d.name")
    List<String> findPossibleDiagnoses(@Param("symptoms") List<String> symptoms);
}