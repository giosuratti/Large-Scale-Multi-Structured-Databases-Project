package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.mongo.DiagnosisAnalyticsDTO;
import it.unipi.findyourdoc.dto.mongo.RatingCorrelationDTO;
import it.unipi.findyourdoc.dto.mongo.SymptomCountDTO;
import it.unipi.findyourdoc.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImplementation implements AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(DoctorServiceImplementation.class);

    public List<SymptomCountDTO> getMostReportedSymptoms(String city, LocalDateTime start, LocalDateTime end){

        // 1. Define the Aggregation Pipeline
        Aggregation aggregation = Aggregation.newAggregation(
                // Stage 1: Filter by city and time range
                match(Criteria.where("patientLocation.city").is(city)
                        .and("createdAt").gte(start).lte(end)),

                // Stage 2: Deconstruct the 'symptoms' array field from the documents
                unwind("symptoms"),

                // Stage 3: Group by symptom name and count occurrences
                group("symptoms").count().as("count"),

                // Stage 4: Project the results into the DTO fields
                // The '_id' from the group stage is the symptom name
                project("count").and("_id").as("symptom"),

                // Stage 5: Sort by count in descending order (most frequent first)
                sort(Sort.Direction.DESC, "count")
        );

        // 2. Execute the aggregation against the "symptom_reports" collection
        // Replace "symptom_reports" with the actual name of your collection
        AggregationResults<SymptomCountDTO> results = mongoTemplate.aggregate(
                aggregation, "symptom_reports", SymptomCountDTO.class
        );

        return results.getMappedResults();
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    // REDIS: Cachiamo il risultato.
    // La chiave sarà composta dai parametri, es: "analytics_diagnosis::{20, 30, M}"
    // Se un admin chiede la stessa fascia d'età tra 5 minuti, Redis risponde subito.
    @Cacheable(value = "analytics_diagnosis", key = "{#minAge, #maxAge, #gender}")
    public List<DiagnosisAnalyticsDTO> getDiagnosisAnalytics(Integer minAge, Integer maxAge, String gender) {

        log.info("Calcolo Analytics Diagnosi per range {}-{} e genere {}", minAge, maxAge, gender);

        // 1. FASE MATCH: Filtriamo i documenti PRIMA di elaborarli (Performance)
        Criteria criteria = new Criteria();

        // Gestione opzionale dei parametri (se sono null, li ignoriamo o mettiamo default)
        if (minAge != null && maxAge != null) {
            criteria.and("patientAge").gte(minAge).lte(maxAge);
        }

        if (gender != null && !gender.isEmpty() && !"ALL".equalsIgnoreCase(gender)) {
            criteria.and("patientGender").is(gender);
        }

        MatchOperation matchStage = Aggregation.match(criteria);

        // 2. FASE UNWIND: I report hanno una LISTA di diagnosi possibili.
        // Dobbiamo "esplodere" l'array per contare ogni diagnosi singolarmente.
        // Es: Doc1 ha ["Influenza", "Covid"] -> Diventa 2 righe separate per il conteggio.
        UnwindOperation unwindStage = Aggregation.unwind("possibleDiagnosies");

        // 3. FASE GROUP: Raggruppiamo per nome della diagnosi e contiamo
        GroupOperation groupStage = Aggregation.group("possibleDiagnosies") // Raggruppa per il valore della stringa
                .count().as("count");        // Conta le occorrenze

        // 4. FASE SORT: Ordiniamo dalle più frequenti alle meno frequenti
        SortOperation sortStage = Aggregation.sort(Sort.Direction.DESC, "count");

        // 5. FASE PROJECT: Mappiamo il risultato nel DTO
        // "_id" contiene il nome della diagnosi dopo il raggruppamento
        ProjectionOperation projectStage = Aggregation.project()
                .and("_id").as("diagnosis")
                .and("count").as("count")
                .andExclude("_id");

        // Opzionale: Limitiamo ai top 10 risultati per pulizia
        LimitOperation limitStage = Aggregation.limit(10);

        // Esecuzione Pipeline
        Aggregation aggregation = Aggregation.newAggregation(
                matchStage,
                unwindStage,
                groupStage,
                sortStage,
                limitStage, // Togli se vuoi tutte le diagnosi
                projectStage
        );

        AggregationResults<DiagnosisAnalyticsDTO> result = mongoTemplate.aggregate(
                aggregation,
                "symptom_reports", // Nome della collezione su Mongo
                DiagnosisAnalyticsDTO.class // Classe di output
        );

        return result.getMappedResults();
    }

    @Override
    public List<RatingCorrelationDTO> getRatingAppointmentCorrelation() {

        // 1. FASE PROIEZIONE INIZIALE
        // Calcoliamo la media dei voti (che sono dentro la lista 'ratings')
        // e prepariamo i campi base.
        ProjectionOperation projectStage1 = project()
                .and("_id").as("doctorId")
                // Concatena Nome e Cognome
                .andExpression("concat(firstName, ' ', lastName)").as("doctorFullName")
                // MongoDB calcola la media del campo 'rating' dentro l'array 'ratings'
                .and(AccumulatorOperators.Avg.avgOf("ratings.rating")).as("averageRating");

        // 2. FASE LOOKUP (La Join tra collezioni)
        // "Guarda nella collezione 'appointments',
        // dove il campo 'doctorId' corrisponde al mio '_id' (doctorId),
        // e metti i risultati in una lista chiamata 'booked_apps'"
        LookupOperation lookupStage = lookup("appointments", "_id", "doctorId", "booked_apps");

        // 3. FASE CONTEGGIO E PULIZIA
        // Manteniamo i dati di prima, ma trasformiamo la lista 'booked_apps' nella sua dimensione (size)
        ProjectionOperation projectStage2 = project("doctorId", "doctorFullName", "averageRating")
                .and("booked_apps").size().as("appointmentCount");

        // 4. FASE ORDINAMENTO (Opzionale)
        // Ordiniamo per chi ha più appuntamenti (così vediamo i più attivi in alto)
        SortOperation sortStage = sort(Sort.Direction.DESC, "appointmentCount");

        // ESECUZIONE
        Aggregation aggregation = newAggregation(
                projectStage1,
                lookupStage,
                projectStage2,
                sortStage
        );

        return mongoTemplate.aggregate(aggregation, "doctors", RatingCorrelationDTO.class).getMappedResults();
    }
}
