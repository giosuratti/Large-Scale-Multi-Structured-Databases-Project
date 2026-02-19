package it.unipi.findyourdoc.service;

import it.unipi.findyourdoc.dto.mongo.CancellationStatsDTO;
import it.unipi.findyourdoc.dto.mongo.DiagnosisAnalyticsDTO;
import it.unipi.findyourdoc.dto.mongo.SymptomCountDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

/**
 * Service interface for retrieving analytical data and statistics.
 * Provides methods to aggregate data regarding symptoms, cancellations, and diagnoses.
 */
public interface AnalyticsService {

    /**
     * Retrieves a paginated list of the most frequently reported symptoms within a specific city and timeframe.
     */
    Page<SymptomCountDTO> getMostReportedSymptoms(String city, LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Retrieves paginated statistics on the medical specializations with the highest appointment cancellation rates.
     */
    Page<CancellationStatsDTO> getTopCancelledSpecializations(Pageable pageable);

    /**
     * Retrieves paginated analytics regarding diagnoses, optionally filtered by patient demographics (age range and gender).
     */
    Page<DiagnosisAnalyticsDTO> getDiagnosisAnalytics(Integer minAge, Integer maxAge, String gender, Pageable pageable);
}