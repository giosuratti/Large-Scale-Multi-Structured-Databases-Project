package it.unipi.findyourdoc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unipi.findyourdoc.dto.mongo.CancellationStatsDTO;
import it.unipi.findyourdoc.dto.mongo.DiagnosisAnalyticsDTO;
import it.unipi.findyourdoc.dto.mongo.SymptomCountDTO;
import it.unipi.findyourdoc.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * Controller for platform-wide analytics and medical statistics.
 * Access is restricted to users with the ADMIN role.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Endpoints for medical and platform statistics")
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);
    /**
     * Retrieves the most reported symptoms within a specific geographic area and timeframe.
     * Useful for tracking disease outbreaks or regional health trends.
     */
    @Operation(summary = "Most reported symptoms in an area (Paginated)")
    @GetMapping("/symptoms/top")
    public ResponseEntity<Page<SymptomCountDTO>> getTopSymptoms(
            @RequestParam String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @ParameterObject Pageable pageable) {

        long startTime = System.currentTimeMillis();
        Page<SymptomCountDTO> data = analyticsService.getMostReportedSymptoms(city, start, end, pageable);
        long duration = System.currentTimeMillis() - startTime;
        log.info("duration = " + duration);

        return ResponseEntity.ok(data);
    }

    /**
     * Aggregates diagnosis frequencies filtered by demographic data (age range and gender).
     */
    @Operation(summary = "Diagnosis frequency by age and gender")
    @GetMapping("/diagnoses/demographic")
    public ResponseEntity<Page<DiagnosisAnalyticsDTO>> getDiagnosisByDemographic(
            @RequestParam int minAge,
            @RequestParam int maxAge,
            @RequestParam String gender,
            @ParameterObject Pageable pageable) {

        long startTime = System.currentTimeMillis();
        Page<DiagnosisAnalyticsDTO> data = analyticsService.getDiagnosisAnalytics(minAge, maxAge, gender, pageable);
        long duration = System.currentTimeMillis() - startTime;

        log.info("duration = " + duration);
        return ResponseEntity.ok(data);
    }

    /**
     * Identifies specializations with the highest rate of appointment cancellations.
     * Helps in monitoring platform efficiency and doctor availability issues.
     */
    @Operation(summary = "Top specializations by cancellations (Paginated)",
            description = "Paginated ranking of medical specializations with the most cancelled appointments.")
    @GetMapping("/specializations/cancelled/top")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<CancellationStatsDTO>> getTopCancelledSpecializations(
            @ParameterObject Pageable pageable
    ) {

        long startTime = System.currentTimeMillis();
        Page<CancellationStatsDTO> data = analyticsService.getTopCancelledSpecializations(pageable);
        long duration = System.currentTimeMillis() - startTime;

        log.info("duration = " + duration);
        return ResponseEntity.ok(data);
    }
}