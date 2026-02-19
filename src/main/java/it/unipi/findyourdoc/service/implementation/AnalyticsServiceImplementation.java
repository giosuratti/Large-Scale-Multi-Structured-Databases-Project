package it.unipi.findyourdoc.service.implementation;

import com.mongodb.ReadPreference;
import it.unipi.findyourdoc.dto.mongo.CancellationStatsDTO;
import it.unipi.findyourdoc.dto.mongo.DiagnosisAnalyticsDTO;
import it.unipi.findyourdoc.dto.mongo.SymptomCountDTO;
import it.unipi.findyourdoc.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Service implementation for complex data analytics using MongoDB Aggregation Framework.
 * Configured to use secondary nodes for read operations to preserve primary node performance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImplementation implements AnalyticsService {

    private final MongoTemplate mongoTemplate;

    /**
     * Configures aggregation to prefer reading from secondary replica set members.
     * Essential for offloading heavy analytics workloads from the primary node.
     */
    private AggregationOptions getSecondaryReadOptions() {
        return AggregationOptions.builder()
                .readPreference(ReadPreference.secondaryPreferred())
                .build();
    }

    /**
     * Aggregates symptom frequency based on location and time range.
     * Uses $unwind to deconstruct symptom arrays for accurate counting.
     */
    @Override
    public Page<SymptomCountDTO> getMostReportedSymptoms(String city, LocalDateTime start, LocalDateTime end, Pageable pageable) {
        List<AggregationOperation> baseOperations = new ArrayList<>();

        // 1. Filter reports by city and date range
        baseOperations.add(match(Criteria.where("patientLocation").is(city)
                .and("createdAt").gte(start).lte(end)));
        // 2. Deconstruct the symptoms array into individual documents
        baseOperations.add(unwind("symptoms"));
        // 3. Group by symptom name and count occurrences
        baseOperations.add(group("symptoms").count().as("count"));
        // 4. Rename fields for DTO compatibility
        baseOperations.add(project("count").and("_id").as("symptom"));

        // Handle sorting
        if (pageable.getSort().isSorted()) {
            baseOperations.add(sort(pageable.getSort()));
        } else {
            baseOperations.add(sort(Sort.Direction.DESC, "count"));
        }

        List<AggregationOperation> dataPipeline = new ArrayList<>(baseOperations);

        // Pagination: Skip and Limit
        if (pageable.isPaged()) {
            dataPipeline.add(skip((long) pageable.getPageNumber() * pageable.getPageSize()));
            dataPipeline.add(limit(pageable.getPageSize()));
        }

        Aggregation aggregation = Aggregation.newAggregation(dataPipeline).withOptions(getSecondaryReadOptions());
        AggregationResults<SymptomCountDTO> results = mongoTemplate.aggregate(aggregation, "symptom_reports", SymptomCountDTO.class);

        return PageableExecutionUtils.getPage(
                results.getMappedResults(),
                pageable,
                () -> countTotalSymptoms(city, start, end)
        );
    }

    /**
     * Counts unique symptom groups to support pagination.
     */
    private long countTotalSymptoms(String city, LocalDateTime start, LocalDateTime end) {
        Aggregation countAggregation = Aggregation.newAggregation(
                match(Criteria.where("patientLocation").is(city).and("createdAt").gte(start).lte(end)),
                unwind("symptoms"),
                group("symptoms"),
                count().as("total")
        ).withOptions(getSecondaryReadOptions());

        return mongoTemplate.aggregate(countAggregation, "symptom_reports", org.bson.Document.class)
                .getMappedResults().size();
    }

    /**
     * Analyzes diagnosis frequency filtered by demographics (age range and gender).
     */
    @Override
    public Page<DiagnosisAnalyticsDTO> getDiagnosisAnalytics(Integer minAge, Integer maxAge, String gender, Pageable pageable) {
        log.info("Analyzing diagnoses for age {}-{} and gender {}", minAge, maxAge, gender);

        Criteria criteria = new Criteria();
        if (minAge != null && maxAge != null) criteria.and("patientAge").gte(minAge).lte(maxAge);
        if (gender != null && !"ALL".equalsIgnoreCase(gender)) criteria.and("patientGender").is(gender);

        List<AggregationOperation> baseOperations = new ArrayList<>();
        // 1. Filter by patient demographics
        baseOperations.add(match(criteria));
        // 2. Deconstruct the diagnosis array
        baseOperations.add(unwind("possibleDiagnosies"));
        // 3. Count occurrences per diagnosis
        baseOperations.add(group("possibleDiagnosies").count().as("count"));

        List<AggregationOperation> dataPipeline = new ArrayList<>(baseOperations);

        if (pageable.getSort().isSorted()) {
            dataPipeline.add(sort(pageable.getSort()));
        } else {
            dataPipeline.add(sort(Sort.Direction.DESC, "count"));
        }

        dataPipeline.add(project().and("_id").as("diagnosis").and("count").as("frequency").andExclude("_id"));

        if (pageable.isPaged()) {
            dataPipeline.add(skip((long) pageable.getPageNumber() * pageable.getPageSize()));
            dataPipeline.add(limit(pageable.getPageSize()));
        }

        Aggregation dataAggregation = newAggregation(dataPipeline).withOptions(getSecondaryReadOptions());
        AggregationResults<DiagnosisAnalyticsDTO> results = mongoTemplate.aggregate(dataAggregation, "symptom_reports", DiagnosisAnalyticsDTO.class);

        return PageableExecutionUtils.getPage(
                results.getMappedResults(),
                pageable,
                () -> {
                    // Count unique diagnosis groups for pagination
                    List<AggregationOperation> countPipeline = new ArrayList<>(baseOperations);
                    countPipeline.add(count().as("total"));

                    AggregationResults<org.bson.Document> countResult = mongoTemplate.aggregate(
                            newAggregation(countPipeline).withOptions(getSecondaryReadOptions()), "symptom_reports", org.bson.Document.class);

                    org.bson.Document uniqueResult = countResult.getUniqueMappedResult();
                    return uniqueResult != null ? ((Number) uniqueResult.get("total")).longValue() : 0L;
                }
        );
    }

    /**
     * Calculates cancellation statistics per specialization.
     * Includes metrics for total cancellations, average patient age, and average doctor rating.
     */
    @Override
    public Page<CancellationStatsDTO> getTopCancelledSpecializations(Pageable pageable) {
        List<AggregationOperation> basePipeline = new ArrayList<>();

        // 1. Filter only cancelled appointments
        basePipeline.add(match(Criteria.where("status").is("CANCELLED")));
        // 2. Deconstruct specialties array
        basePipeline.add(unwind("specialties"));
        // 3. Aggregate totals and averages (age and rating) per specialization
        basePipeline.add(group("specialties")
                .count().as("totalCancelled")
                .avg("patientAge").as("avgPatientAge")
                .avg("doctorRating").as("avgDoctorRating")
        );

        List<AggregationOperation> dataPipeline = new ArrayList<>(basePipeline);

        // 4. Project results and round numeric values for the DTO
        dataPipeline.add(project()
                .and("_id").as("specialization")
                .and("totalCancelled").as("totalCancelled")
                .andExpression("round(avgPatientAge, 1)").as("avgPatientAge")
                .andExpression("round(avgDoctorRating, 1)").as("avgDoctorRating")
        );

        if (pageable.getSort().isSorted()) {
            dataPipeline.add(sort(pageable.getSort()));
        } else {
            dataPipeline.add(sort(Sort.Direction.DESC, "totalCancelled"));
        }

        if (pageable.isPaged()) {
            dataPipeline.add(skip((long) pageable.getPageNumber() * pageable.getPageSize()));
            dataPipeline.add(limit(pageable.getPageSize()));
        }

        Aggregation aggregation = Aggregation.newAggregation(dataPipeline).withOptions(getSecondaryReadOptions());
        AggregationResults<CancellationStatsDTO> results = mongoTemplate.aggregate(aggregation, "appointments", CancellationStatsDTO.class);

        return PageableExecutionUtils.getPage(
                results.getMappedResults(),
                pageable,
                () -> {
                    // Count unique specialization groups affected by cancellations
                    List<AggregationOperation> countPipeline = new ArrayList<>();
                    countPipeline.add(match(Criteria.where("status").is("CANCELLED")));
                    countPipeline.add(unwind("specialties"));
                    countPipeline.add(group("specialties"));

                    Aggregation countAgg = newAggregation(countPipeline).withOptions(getSecondaryReadOptions());
                    return mongoTemplate.aggregate(countAgg, "appointments", org.bson.Document.class)
                            .getMappedResults().size();
                }
        );
    }
}