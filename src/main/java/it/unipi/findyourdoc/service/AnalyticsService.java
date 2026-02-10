package it.unipi.findyourdoc.service;

import it.unipi.findyourdoc.dto.mongo.CancellationStatsDTO;
import it.unipi.findyourdoc.dto.mongo.DiagnosisAnalyticsDTO;
import it.unipi.findyourdoc.dto.mongo.SymptomCountDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface AnalyticsService {
    Page<SymptomCountDTO> getMostReportedSymptoms(String city, LocalDateTime start, LocalDateTime end, Pageable pageable);
    //List<RatingCorrelationDTO> getRatingAppointmentCorrelation();
    Page<CancellationStatsDTO> getTopCancelledSpecializations(Pageable pageable);
    Page<DiagnosisAnalyticsDTO> getDiagnosisAnalytics(Integer minAge, Integer maxAge, String gender, Pageable pageable);
}
