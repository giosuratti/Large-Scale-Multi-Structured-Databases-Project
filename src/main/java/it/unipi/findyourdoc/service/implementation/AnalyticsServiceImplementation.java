package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.mongo.DiagnosisAnalyticsDTO;
import it.unipi.findyourdoc.dto.mongo.RatingCorrelationDTO;
import it.unipi.findyourdoc.dto.mongo.SymptomCountDTO;
import it.unipi.findyourdoc.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImplementation implements AnalyticsService {

    public List<SymptomCountDTO> getMostReportedSymptoms(String city, LocalDateTime start, LocalDateTime end){

    }

    public List<DiagnosisAnalyticsDTO> getDiagnosisAnalytics(Integer minAge, Integer maxAge, String gender){

    }

    public List<RatingCorrelationDTO> getRatingAppointmentCorrelation(){

    }
}
