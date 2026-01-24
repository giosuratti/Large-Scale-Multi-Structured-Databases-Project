package it.unipi.findyourdoc.service;

import it.unipi.findyourdoc.dto.mongo.DiagnosisAnalyticsDTO;
import it.unipi.findyourdoc.dto.mongo.RatingCorrelationDTO;
import it.unipi.findyourdoc.dto.mongo.SymptomCountDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface AnalyticsService {
    List<SymptomCountDTO> getMostReportedSymptoms(String city, LocalDateTime start, LocalDateTime end);
    List<RatingCorrelationDTO> getRatingAppointmentCorrelation();
    List<DiagnosisAnalyticsDTO> getDiagnosisAnalytics(Integer minAge, Integer maxAge, String gender);
}
