package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.mongo.CancellationStatsDTO;
import it.unipi.findyourdoc.dto.mongo.DiagnosisAnalyticsDTO;
import it.unipi.findyourdoc.dto.mongo.SymptomCountDTO;
import it.unipi.findyourdoc.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImplementation implements AnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(DoctorServiceImplementation.class);

    public Page<SymptomCountDTO> getMostReportedSymptoms(String city, LocalDateTime start, LocalDateTime end, Pageable pageable) {

        // 1. Definiamo le operazioni BASE (comuni sia al conteggio che al recupero dati)
        // Nota: L'ordine è importante.
        List<AggregationOperation> baseOperations = new ArrayList<>();

        // Stage 1: Filtro
        baseOperations.add(match(Criteria.where("patientLocation").is(city)
                .and("createdAt").gte(start).lte(end)));

        // Stage 2: Unwind (esplode l'array symptoms)
        baseOperations.add(unwind("symptoms"));

        // Stage 3: Group (raggruppa per nome sintomo e conta)
        baseOperations.add(group("symptoms").count().as("count"));

        // Stage 4: Project (mappa i campi nel DTO)
        baseOperations.add(project("count").and("_id").as("symptom"));

        // Stage 5: Sort (ordinamento richiesto dal business o dal pageable)
        // Se il pageable ha un sort, usiamo quello, altrimenti default DESC su count
        if (pageable.getSort().isSorted()) {
            baseOperations.add(sort(pageable.getSort()));
        } else {
            baseOperations.add(sort(Sort.Direction.DESC, "count"));
        }

        // --- COSTRUZIONE PIPELINE PER I DATI (PAGINATI) ---
        List<AggregationOperation> dataPipeline = new ArrayList<>(baseOperations);

        // Stage 6 & 7: Pagination (Skip & Limit)
        // Importante: si applicano DOPO il raggruppamento
        if (pageable.isPaged()) {
            dataPipeline.add(skip((long) pageable.getPageNumber() * pageable.getPageSize()));
            dataPipeline.add(limit(pageable.getPageSize()));
        }

        Aggregation aggregation = Aggregation.newAggregation(dataPipeline);

        // Esecuzione Query Dati
        AggregationResults<SymptomCountDTO> results = mongoTemplate.aggregate(
                aggregation, "symptom_reports", SymptomCountDTO.class
        );

        List<SymptomCountDTO> dataList = results.getMappedResults();

        // --- COSTRUZIONE PAGE OBJECT ---
        // Usiamo PageableExecutionUtils per calcolare il totale SOLO se necessario
        // (evita la query di count se siamo alla prima pagina e i risultati sono < page size)
        return PageableExecutionUtils.getPage(
                dataList,
                pageable,
                () -> countTotalSymptoms(city, start, end)
        );
    }

    /**
     * Metodo helper per contare il numero totale di gruppi (sintomi unici)
     * che soddisfano i criteri. Necessario per la paginazione.
     */
    private long countTotalSymptoms(String city, LocalDateTime start, LocalDateTime end) {
        Aggregation countAggregation = Aggregation.newAggregation(
                match(Criteria.where("patientLocation").is(city)
                        .and("createdAt").gte(start).lte(end)),
                unwind("symptoms"),
                group("symptoms"), // Raggruppiamo solo per ottenere gli univoci
                count().as("total") // Contiamo quanti gruppi sono usciti
                // Nota: qui non serve sort o project
        );

        // Il risultato sarà un oggetto con un campo "total"
        AggregationResults<org.bson.Document> countResults = mongoTemplate.aggregate(
                countAggregation, "symptom_reports", org.bson.Document.class
        );

        // Se la lista è vuota, 0, altrimenti prendiamo il valore "total"
        // Ma attenzione: il 'count().as("total")' dopo un group restituisce N documenti
        // che hanno un campo total=1? No.
        // L'approccio migliore per contare i gruppi risultanti è usare .count() sull'aggregazione o
        // proiettare e contare la dimensione della lista risultante.

        // FIX LOGICA COUNT:
        // Il modo più semplice per contare quanti gruppi escono da un'aggregazione è questo:
        return countResults.getMappedResults().size();
    }

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
// REDIS: Cache the result.
// Key now includes page number and size to distinguish between pages.
    @Cacheable(value = "analytics_diagnosis", key = "{#minAge, #maxAge, #gender, #pageable.pageNumber, #pageable.pageSize}")
    public Page<DiagnosisAnalyticsDTO> getDiagnosisAnalytics(Integer minAge, Integer maxAge, String gender, Pageable pageable) {

        log.info("Calculating Diagnosis Analytics for range {}-{}, gender {}, page {}", minAge, maxAge, gender, pageable.getPageNumber());

        // --- STEP 1: DEFINE CRITERIA (Common for both data and count) ---
        Criteria criteria = new Criteria();

        // Optional parameter handling
        if (minAge != null && maxAge != null) {
            criteria.and("patientAge").gte(minAge).lte(maxAge);
        }

        if (gender != null && !gender.isEmpty() && !"ALL".equalsIgnoreCase(gender)) {
            criteria.and("patientGender").is(gender);
        }

        // --- STEP 2: BUILD BASE PIPELINE (Operations needed for both counting and fetching) ---
        List<AggregationOperation> baseOperations = new ArrayList<>();

        // 2.1 MATCH: Filter documents BEFORE processing (Performance)
        baseOperations.add(match(criteria));

        // 2.2 UNWIND: Explode the 'possibleDiagnosies' array.
        // e.g., Doc1 has ["Flu", "Covid"] -> Becomes 2 separate documents for counting.
        baseOperations.add(unwind("possibleDiagnosies"));

        // 2.3 GROUP: Group by diagnosis name and count occurrences
        baseOperations.add(group("possibleDiagnosies").count().as("count"));

        // --- STEP 3: BUILD DATA PIPELINE (Specific for fetching the page) ---
        List<AggregationOperation> dataPipeline = new ArrayList<>(baseOperations);

        // 3.1 SORT: Order by frequency (DESC) or use the Pageable sort if provided
        if (pageable.getSort().isSorted()) {
            dataPipeline.add(sort(pageable.getSort()));
        } else {
            dataPipeline.add(sort(Sort.Direction.DESC, "count"));
        }

        // 3.2 PROJECT: Map results to DTO
        // FIX FOR NULL FREQUENCY: We explicitly map the internal "count" to the "frequency" field expected by DTO.
        dataPipeline.add(project()
                .and("_id").as("diagnosis") // Map group ID (the diagnosis name) to 'diagnosis'
                .and("count").as("frequency") // Map the count to 'frequency' (Matches your DTO)
                .andExclude("_id"));

        // 3.3 PAGINATION: Apply Skip and Limit
        if (pageable.isPaged()) {
            dataPipeline.add(skip((long) pageable.getPageNumber() * pageable.getPageSize()));
            dataPipeline.add(limit(pageable.getPageSize()));
        }

        // Execute Data Query
        Aggregation dataAggregation = newAggregation(dataPipeline);
        AggregationResults<DiagnosisAnalyticsDTO> results = mongoTemplate.aggregate(
                dataAggregation,
                "symptom_reports",
                DiagnosisAnalyticsDTO.class
        );

        // --- STEP 4: CALCULATE TOTAL COUNT (Required for Page object) ---
        // We use PageableExecutionUtils. It's smart: it only runs the count query if necessary.
        return PageableExecutionUtils.getPage(
                results.getMappedResults(),
                pageable,
                () -> {
                    // To count distinct diagnoses, we need to aggregate again without skip/limit
                    List<AggregationOperation> countPipeline = new ArrayList<>(baseOperations);

                    // We just need to count how many groups resulted from the base operations
                    countPipeline.add(count().as("total"));

                    Aggregation countAggregation = newAggregation(countPipeline);
                    AggregationResults<org.bson.Document> countResult = mongoTemplate.aggregate(
                            countAggregation,
                            "symptom_reports",
                            org.bson.Document.class
                    );

                    // If result is empty, total is 0. Otherwise, get the "total" field.
                    // Note: The previous group stage outputs N documents (one per diagnosis).
                    // The 'count().as("total")' stage counts those documents.
                    // So the result list will contain exactly one document: { "total": <number> }
                    org.bson.Document uniqueResult = countResult.getUniqueMappedResult();
                    return uniqueResult != null ? ((Number) uniqueResult.get("total")).longValue() : 0L;
                }
        );
    }

    /*@Override
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
    }*/

    @Override
    public Page<CancellationStatsDTO> getTopCancelledSpecializations(Pageable pageable) {

        // --- 1. OPERAZIONI BASE (Comuni a Dati e Conteggio) ---
        List<AggregationOperation> basePipeline = new ArrayList<>();

        // STAGE 1: MATCH (Filtra solo cancellati)
        basePipeline.add(Aggregation.match(Criteria.where("status").is("CANCELLED")));

        // STAGE 2: UNWIND (Esplode l'array delle specializzazioni)
        basePipeline.add(Aggregation.unwind("specialties"));

        // STAGE 3: GROUP (Raggruppa per nome spec)
        basePipeline.add(Aggregation.group("specialties")
                .count().as("totalCancelled")
                .avg("patientAge").as("avgPatientAge")
                .avg("doctorRating").as("avgDoctorRating")
        );

        // --- 2. PIPELINE DATI (Specifica per la pagina richiesta) ---
        List<AggregationOperation> dataPipeline = new ArrayList<>(basePipeline);

        // STAGE 4: PROJECT (Formatta i dati prima di ordinare/paginare)
        dataPipeline.add(Aggregation.project()
                .and("_id").as("specialization")
                .and("totalCancelled").as("totalCancelled")
                // Arrotondamento per pulizia
                .andExpression("round(avgPatientAge, 1)").as("avgPatientAge")
                .andExpression("round(avgDoctorRating, 1)").as("avgDoctorRating")
        );

        // STAGE 5: SORT (Ordinamento)
        // Se il frontend chiede un ordinamento specifico (es. per nome), usiamo quello.
        // Altrimenti default: chi ha più cancellazioni in alto.
        if (pageable.getSort().isSorted()) {
            dataPipeline.add(Aggregation.sort(pageable.getSort()));
        } else {
            dataPipeline.add(Aggregation.sort(Sort.Direction.DESC, "totalCancelled"));
        }

        // STAGE 6: PAGINAZIONE (Skip & Limit)
        if (pageable.isPaged()) {
            dataPipeline.add(Aggregation.skip((long) pageable.getPageNumber() * pageable.getPageSize()));
            dataPipeline.add(Aggregation.limit(pageable.getPageSize()));
        }

        // ESECUZIONE QUERY DATI
        Aggregation aggregation = Aggregation.newAggregation(dataPipeline);
        AggregationResults<CancellationStatsDTO> results = mongoTemplate.aggregate(
                aggregation, "appointments", CancellationStatsDTO.class
        );

        // --- 3. CALCOLO TOTALE (Per l'oggetto Page) ---
        // Dobbiamo sapere quante specializzazioni uniche hanno almeno una cancellazione.
        return PageableExecutionUtils.getPage(
                results.getMappedResults(),
                pageable,
                () -> {
                    // Pipeline leggera per contare i gruppi
                    List<AggregationOperation> countPipeline = new ArrayList<>();
                    countPipeline.add(Aggregation.match(Criteria.where("status").is("CANCELLED")));
                    countPipeline.add(Aggregation.unwind("specialties"));
                    countPipeline.add(Aggregation.group("specialties")); // Raggruppa solo per ID
                    // countPipeline.add(Aggregation.count().as("total")); // Opzionale in Mongo moderno

                    // Contiamo la dimensione della lista risultante (numero di specializzazioni uniche)
                    Aggregation countAgg = Aggregation.newAggregation(countPipeline);
                    return (long) mongoTemplate.aggregate(countAgg, "appointments", org.bson.Document.class)
                            .getMappedResults().size();
                }
        );
    }
}
