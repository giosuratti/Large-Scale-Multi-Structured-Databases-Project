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
    // 1. Trova le malattie collegate ai sintomi inseriti
    MATCH (s:Symptom)-[r:INDICATES_DISEASE]->(d:Disease)
    WHERE s.name IN inputSymptoms
    
    // 2. Raggruppa per malattia e raccogli le probabilità
    // d.prior = Probabilità a priori della malattia (calcolata in Python)
    // r.probability = Probabilità che quel sintomo appaia in quella malattia (calcolata in Python)
    WITH d, 
         d.prior AS prior_prob, 
         collect(r.probability) AS symptom_probs
    
    // 3. Calcolo Score Bayesiano (Log-Likelihood)
    // Formula: Score = log(Prior) + SUM(log(P(Sintomo|Malattia)))
    // Usiamo 'reduce' per sommare i logaritmi della lista symptom_probs
    WITH d, 
         reduce(score = log(prior_prob), prob IN symptom_probs | score + log(prob)) AS bayesScore
    
    // 4. Ordina dal punteggio più alto (meno negativo) al più basso
    ORDER BY bayesScore DESC
    LIMIT 5
    
    RETURN d.name
""")
    ArrayList<String> findPossibleDiagnoses(@Param("symptoms") List<String> symptoms);
}