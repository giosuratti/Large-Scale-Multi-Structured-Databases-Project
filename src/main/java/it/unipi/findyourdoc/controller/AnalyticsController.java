package it.unipi.findyourdoc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unipi.findyourdoc.dto.mongo.DiagnosisAnalyticsDTO;
import it.unipi.findyourdoc.dto.mongo.RatingCorrelationDTO;
import it.unipi.findyourdoc.dto.mongo.SymptomCountDTO;
import it.unipi.findyourdoc.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Endpoints for medical and platform statistics")
@PreAuthorize("hasRole('ADMIN')") // Solo gli admin accedono alle analytics
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "Most reported symptoms in an area")
    @GetMapping("/symptoms/top")
    public ResponseEntity<List<SymptomCountDTO>> getTopSymptoms(
            @RequestParam String city,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(analyticsService.getMostReportedSymptoms(city, start, end));
    }

    @Operation(summary = "Correlation between ratings and appointments")
    @GetMapping("/correlation/rating-appointments")
    public ResponseEntity<List<RatingCorrelationDTO>> getRatingCorrelation() {
        return ResponseEntity.ok(analyticsService.getRatingAppointmentCorrelation());
    }

    @Operation(summary = "Diagnosis frequency by age and gender")
    @GetMapping("/diagnoses/demographic")
    public ResponseEntity<List<DiagnosisAnalyticsDTO>> getDiagnosisByDemographic(
            @RequestParam int minAge,
            @RequestParam int maxAge,
            @RequestParam String gender) {
        return ResponseEntity.ok(analyticsService.getDiagnosisAnalytics(minAge, maxAge, gender));
    }
}