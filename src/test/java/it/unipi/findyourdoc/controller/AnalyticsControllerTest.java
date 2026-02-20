package it.unipi.findyourdoc.controller;

import it.unipi.findyourdoc.dto.mongo.CancellationStatsDTO;
import it.unipi.findyourdoc.dto.mongo.DiagnosisAnalyticsDTO;
import it.unipi.findyourdoc.dto.mongo.SymptomCountDTO;
import it.unipi.findyourdoc.security.JwtTokenProvider;
import it.unipi.findyourdoc.service.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for the AnalyticsController.
 * Security filters are bypassed to focus exclusively on endpoint routing,
 * parameter parsing, and JSON serialization.
 */
@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerTest {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsControllerTest.class);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsService analyticsService;

    // Mocked to prevent ApplicationContext initialization failures
    // in case security auto-configurations attempt to wire it.
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        log.debug("Initializing mock environment for AnalyticsControllerTest.");
    }

    @Test
    void getTopSymptoms_ShouldReturnPaginatedSymptoms() throws Exception {
        log.info("Testing endpoint: GET /api/analytics/symptoms/top");

        SymptomCountDTO mockSymptom = new SymptomCountDTO();
        mockSymptom.setSymptom("Headache");
        mockSymptom.setCount(150L);

        when(analyticsService.getMostReportedSymptoms(
                anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mockSymptom)));

        // Setup ISO Date strings for the request parameters
        String startDate = LocalDateTime.now().minusDays(30).format(DateTimeFormatter.ISO_DATE_TIME);
        String endDate = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);

        mockMvc.perform(get("/api/analytics/symptoms/top")
                        .param("city", "Milan")
                        .param("start", startDate)
                        .param("end", endDate)
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].symptom").value("Headache"))
                .andExpect(jsonPath("$.content[0].count").value(150));

        log.debug("Successfully validated top symptoms response mapping.");
    }

    @Test
    void getDiagnosisByDemographic_ShouldReturnPaginatedDiagnoses() throws Exception {
        log.info("Testing endpoint: GET /api/analytics/diagnoses/demographic");

        DiagnosisAnalyticsDTO mockDiagnosis = new DiagnosisAnalyticsDTO();
        mockDiagnosis.setDiagnosis("Hypertension");
        mockDiagnosis.setFrequency(85L);

        when(analyticsService.getDiagnosisAnalytics(
                anyInt(), anyInt(), anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mockDiagnosis)));

        mockMvc.perform(get("/api/analytics/diagnoses/demographic")
                        .param("minAge", "40")
                        .param("maxAge", "60")
                        .param("gender", "M")
                        .param("page", "0")
                        .param("size", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].diagnosis").value("Hypertension"))
                .andExpect(jsonPath("$.content[0].frequency").value(85));

        log.debug("Successfully validated demographic diagnosis response mapping.");
    }

    @Test
    void getTopCancelledSpecializations_ShouldReturnPaginatedStats() throws Exception {
        log.info("Testing endpoint: GET /api/analytics/specializations/cancelled/top");

        CancellationStatsDTO mockStat = new CancellationStatsDTO("Cardiology", 42L, 55.5, 4.2);

        when(analyticsService.getTopCancelledSpecializations(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mockStat)));

        mockMvc.perform(get("/api/analytics/specializations/cancelled/top")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].specialization").value("Cardiology"))
                .andExpect(jsonPath("$.content[0].totalCancelled").value(42))
                .andExpect(jsonPath("$.content[0].avgPatientAge").value(55.5))
                .andExpect(jsonPath("$.content[0].avgDoctorRating").value(4.2));

        log.debug("Successfully validated cancelled specializations response mapping.");
    }
}