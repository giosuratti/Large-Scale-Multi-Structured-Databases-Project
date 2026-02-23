package it.unipi.findyourdoc.repository.neo4j;

import it.unipi.findyourdoc.model.neo4j.DiseaseNode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Disease nodes in Neo4j.
 * Executes advanced graph-based probabilistic inference to evaluate diagnostic outcomes.
 */
public interface DiseaseRepository extends Neo4jRepository<DiseaseNode, String> {

    /**
     * Identifies the most probable diseases based on an input list of symptoms.
     * Implements a Naive Bayes classification model directly within the graph query.
     */

    @Cacheable(value = "dia", key = "#symptoms")
    @Query("""
    WITH $symptoms AS inputSymptoms
    // 1. Match diseases connected to the provided symptoms
    MATCH (s:Symptom)-[r:INDICATES_DISEASE]->(d:Disease)
    WHERE s.name IN inputSymptoms
    
    // 2. Group by disease and collect conditional probabilities
    // d.prior = A priori probability of the disease
    // r.probability = P(Symptom | Disease)
    WITH d, 
         d.prior AS prior_prob, 
         collect(r.probability) AS symptom_probs
    
    // 3. Calculate Bayesian Log-Likelihood Score
    // Using log addition prevents floating-point arithmetic underflow
    WITH d, 
         reduce(score = log(prior_prob), prob IN symptom_probs | score + log(prob)) AS bayesScore
    
    // 4. Order from highest score (least negative) to lowest
    ORDER BY bayesScore DESC
    LIMIT 5
    
    RETURN d.name
    """)
    ArrayList<String> findPossibleDiagnoses(@Param("symptoms") List<String> symptoms);
}