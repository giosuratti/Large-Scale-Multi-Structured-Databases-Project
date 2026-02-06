package it.unipi.findyourdoc.repository.neo4j;

import it.unipi.findyourdoc.model.neo4j.DiseaseNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.List;

public interface DiseaseRepository extends Neo4jRepository<DiseaseNode, String> {

    /**
     * Trova le diagnosi più probabili basandosi su una lista di sintomi.
     * Logica: Conta quanti sintomi in comune ha una malattia con la lista fornita
     * e ordina per numero di corrispondenze decrescente.
     */
    @Query("""
    WITH $symptoms AS inputSymptoms
    MATCH (s:Symptom) WHERE s.name IN inputSymptoms
    MATCH (s)-[r:INDICATES_DISEASE]->(d:Disease)
    WITH d,
         count(s) AS matchedSymptoms,
         sum(r.weight) AS relevanceScore,
         COUNT { (d)<-[:INDICATES_DISEASE]-() } AS totalSymptoms
    WITH d,
         round(relevanceScore * (toFloat(matchedSymptoms) / totalSymptoms), 2) AS probability
    ORDER BY probability DESC
    LIMIT 5
    RETURN d.name
""")
    ArrayList<String> findPossibleDiagnoses(@Param("symptoms") List<String> symptoms);
}