package it.unipi.findyourdoc.service.implementation;

import com.mongodb.ReadPreference;
import it.unipi.findyourdoc.dto.mongo.CancellationStatsDTO;
import it.unipi.findyourdoc.dto.mongo.DiagnosisAnalyticsDTO;
import it.unipi.findyourdoc.dto.mongo.SymptomCountDTO;
import it.unipi.findyourdoc.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationOptions;
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

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Helper per creare opzioni di aggregazione che leggono dai secondari.
     * Evita di ripetere il codice in ogni metodo.
     */
    private AggregationOptions getSecondaryReadOptions() {
        return AggregationOptions.builder()
                .readPreference(ReadPreference.secondaryPreferred())
                .build();
    }

    @Override
    public Page<SymptomCountDTO> getMostReportedSymptoms(String city, LocalDateTime start, LocalDateTime end, Pageable pageable) {

        List<AggregationOperation> baseOperations = new ArrayList<>();

        baseOperations.add(match(Criteria.where("patientLocation").is(city)
                .and("createdAt").gte(start).lte(end)));
        baseOperations.add(unwind("symptoms"));
        baseOperations.add(group("symptoms").count().as("count"));
        baseOperations.add(project("count").and("_id").as("symptom"));

        if (pageable.getSort().isSorted()) {
            baseOperations.add(sort(pageable.getSort()));
        } else {
            baseOperations.add(sort(Sort.Direction.DESC, "count"));
        }

        List<AggregationOperation> dataPipeline = new ArrayList<>(baseOperations);

        if (pageable.isPaged()) {
            dataPipeline.add(skip((long) pageable.getPageNumber() * pageable.getPageSize()));
            dataPipeline.add(limit(pageable.getPageSize()));
        }

        // --- APPLICAZIONE READ PREFERENCE (Secondary Preferred) ---
        Aggregation aggregation = Aggregation.newAggregation(dataPipeline)
                .withOptions(getSecondaryReadOptions()); // <--- QUI

        AggregationResults<SymptomCountDTO> results = mongoTemplate.aggregate(
                aggregation, "symptom_reports", SymptomCountDTO.class
        );

        List<SymptomCountDTO> dataList = results.getMappedResults();

        return PageableExecutionUtils.getPage(
                dataList,
                pageable,
                () -> countTotalSymptoms(city, start, end)
        );
    }

    private long countTotalSymptoms(String city, LocalDateTime start, LocalDateTime end) {
        Aggregation countAggregation = Aggregation.newAggregation(
                match(Criteria.where("patientLocation").is(city)
                        .and("createdAt").gte(start).lte(end)),
                unwind("symptoms"),
                group("symptoms"),
                count().as("total")
        ).withOptions(getSecondaryReadOptions()); // <--- ANCHE QUI SUL COUNT

        AggregationResults<org.bson.Document> countResults = mongoTemplate.aggregate(
                countAggregation, "symptom_reports", org.bson.Document.class
        );

        return countResults.getMappedResults().size();
    }

    @Override
    public Page<DiagnosisAnalyticsDTO> getDiagnosisAnalytics(Integer minAge, Integer maxAge, String gender, Pageable pageable) {

        log.info("Calculating Diagnosis Analytics for range {}-{}, gender {}, page {}", minAge, maxAge, gender, pageable.getPageNumber());

        Criteria criteria = new Criteria();
        if (minAge != null && maxAge != null) {
            criteria.and("patientAge").gte(minAge).lte(maxAge);
        }
        if (gender != null && !gender.isEmpty() && !"ALL".equalsIgnoreCase(gender)) {
            criteria.and("patientGender").is(gender);
        }

        List<AggregationOperation> baseOperations = new ArrayList<>();
        baseOperations.add(match(criteria));
        baseOperations.add(unwind("possibleDiagnosies"));
        baseOperations.add(group("possibleDiagnosies").count().as("count"));

        List<AggregationOperation> dataPipeline = new ArrayList<>(baseOperations);

        if (pageable.getSort().isSorted()) {
            dataPipeline.add(sort(pageable.getSort()));
        } else {
            dataPipeline.add(sort(Sort.Direction.DESC, "count"));
        }

        dataPipeline.add(project()
                .and("_id").as("diagnosis")
                .and("count").as("frequency")
                .andExclude("_id"));

        if (pageable.isPaged()) {
            dataPipeline.add(skip((long) pageable.getPageNumber() * pageable.getPageSize()));
            dataPipeline.add(limit(pageable.getPageSize()));
        }

        // --- APPLICAZIONE READ PREFERENCE ---
        Aggregation dataAggregation = newAggregation(dataPipeline)
                .withOptions(getSecondaryReadOptions()); // <--- QUI

        AggregationResults<DiagnosisAnalyticsDTO> results = mongoTemplate.aggregate(
                dataAggregation,
                "symptom_reports",
                DiagnosisAnalyticsDTO.class
        );

        return PageableExecutionUtils.getPage(
                results.getMappedResults(),
                pageable,
                () -> {
                    List<AggregationOperation> countPipeline = new ArrayList<>(baseOperations);
                    countPipeline.add(count().as("total"));

                    // --- APPLICAZIONE READ PREFERENCE SUL COUNT ---
                    Aggregation countAggregation = newAggregation(countPipeline)
                            .withOptions(getSecondaryReadOptions()); // <--- QUI

                    AggregationResults<org.bson.Document> countResult = mongoTemplate.aggregate(
                            countAggregation,
                            "symptom_reports",
                            org.bson.Document.class
                    );

                    org.bson.Document uniqueResult = countResult.getUniqueMappedResult();
                    return uniqueResult != null ? ((Number) uniqueResult.get("total")).longValue() : 0L;
                }
        );
    }

    @Override
    public Page<CancellationStatsDTO> getTopCancelledSpecializations(Pageable pageable) {

        List<AggregationOperation> basePipeline = new ArrayList<>();
        basePipeline.add(Aggregation.match(Criteria.where("status").is("CANCELLED")));
        basePipeline.add(Aggregation.unwind("specialties"));
        basePipeline.add(Aggregation.group("specialties")
                .count().as("totalCancelled")
                .avg("patientAge").as("avgPatientAge")
                .avg("doctorRating").as("avgDoctorRating")
        );

        List<AggregationOperation> dataPipeline = new ArrayList<>(basePipeline);

        dataPipeline.add(Aggregation.project()
                .and("_id").as("specialization")
                .and("totalCancelled").as("totalCancelled")
                .andExpression("round(avgPatientAge, 1)").as("avgPatientAge")
                .andExpression("round(avgDoctorRating, 1)").as("avgDoctorRating")
        );

        if (pageable.getSort().isSorted()) {
            dataPipeline.add(Aggregation.sort(pageable.getSort()));
        } else {
            dataPipeline.add(Aggregation.sort(Sort.Direction.DESC, "totalCancelled"));
        }

        if (pageable.isPaged()) {
            dataPipeline.add(Aggregation.skip((long) pageable.getPageNumber() * pageable.getPageSize()));
            dataPipeline.add(Aggregation.limit(pageable.getPageSize()));
        }

        // --- APPLICAZIONE READ PREFERENCE ---
        Aggregation aggregation = Aggregation.newAggregation(dataPipeline)
                .withOptions(getSecondaryReadOptions()); // <--- QUI

        AggregationResults<CancellationStatsDTO> results = mongoTemplate.aggregate(
                aggregation, "appointments", CancellationStatsDTO.class
        );

        return PageableExecutionUtils.getPage(
                results.getMappedResults(),
                pageable,
                () -> {
                    List<AggregationOperation> countPipeline = new ArrayList<>();
                    countPipeline.add(Aggregation.match(Criteria.where("status").is("CANCELLED")));
                    countPipeline.add(Aggregation.unwind("specialties"));
                    countPipeline.add(Aggregation.group("specialties"));

                    // --- APPLICAZIONE READ PREFERENCE SUL COUNT ---
                    Aggregation countAgg = Aggregation.newAggregation(countPipeline)
                            .withOptions(getSecondaryReadOptions()); // <--- QUI

                    return (long) mongoTemplate.aggregate(countAgg, "appointments", org.bson.Document.class)
                            .getMappedResults().size();
                }
        );
    }
}