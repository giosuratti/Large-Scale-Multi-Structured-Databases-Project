package it.unipi.findyourdoc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.dto.neo4j.SpecialistDTO;
import it.unipi.findyourdoc.security.JwtTokenFilter;
import it.unipi.findyourdoc.security.JwtTokenProvider;
import it.unipi.findyourdoc.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for the PatientController using MockMvc.
 * Security filters are explicitly excluded to isolate the web layer routing,
 * request parsing, and response serialization.
 */
@WebMvcTest(
        controllers = PatientController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtTokenFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class PatientControllerTest {

    private static final Logger log = LoggerFactory.getLogger(PatientControllerTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PatientService patientService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private final String MOCK_TOKEN = "Bearer mock.jwt.token";
    private final String MOCK_EMAIL = "patient@findyourdoc.it";

    @BeforeEach
    void setUp() {
        log.debug("Initializing test context with mock JWT configurations.");
        when(jwtTokenProvider.resolveToken(any())).thenReturn(MOCK_TOKEN);
        when(jwtTokenProvider.getEmailFromToken(MOCK_TOKEN)).thenReturn(MOCK_EMAIL);
    }

    @Test
    void registerPatient_ShouldReturnCreatedProfile() throws Exception {
        log.info("Testing endpoint: POST /api/patients/register");
        PatientCreateDTO createDTO = new PatientCreateDTO();
        createDTO.setEmail("newpatient@findyourdoc.it");
        createDTO.setFirstName("Mario");

        PatientReadDTO readDTO = new PatientReadDTO();
        readDTO.setEmail("newpatient@findyourdoc.it");

        when(patientService.registerPatient(any(PatientCreateDTO.class))).thenReturn(readDTO);

        mockMvc.perform(post("/api/patients/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("newpatient@findyourdoc.it"));
    }

    @Test
    void updatePatient_ShouldReturnUpdatedProfile() throws Exception {
        log.info("Testing endpoint: PUT /api/patients/me");
        PatientUpdateDTO updateDTO = new PatientUpdateDTO();
        updateDTO.setTelephone("111-222333");

        PatientReadDTO readDTO = new PatientReadDTO();
        readDTO.setEmail(MOCK_EMAIL);
        readDTO.setTelephone("111-222333");

        when(patientService.updatePatient(eq(MOCK_EMAIL), any(PatientUpdateDTO.class))).thenReturn(readDTO);

        mockMvc.perform(put("/api/patients/me")
                        .header("Authorization", MOCK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telephone").value("111-222333"));
    }

    @Test
    void getCurrentPatientProfile_ShouldReturnProfile() throws Exception {
        log.info("Testing endpoint: GET /api/patients/me");
        PatientReadDTO readDTO = new PatientReadDTO();
        readDTO.setEmail(MOCK_EMAIL);
        readDTO.setFirstName("Luigi");

        when(patientService.getPatientByEmail(MOCK_EMAIL)).thenReturn(readDTO);

        mockMvc.perform(get("/api/patients/me")
                        .header("Authorization", MOCK_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Luigi"));
    }

    @Test
    void bookAppointmentById_ShouldReturnAppointment() throws Exception {
        log.info("Testing endpoint: POST /api/patients/book");
        AppointmentBookDTO bookDTO = new AppointmentBookDTO();
        bookDTO.setDoctorId("doc123");

        AppointmentPatientDTO resultDTO = new AppointmentPatientDTO();
        resultDTO.setId("appt_001");
        resultDTO.setDoctorFirstName("Gregory");

        when(patientService.bookAppointmentByEmail(eq(MOCK_EMAIL), any(AppointmentBookDTO.class))).thenReturn(resultDTO);

        mockMvc.perform(post("/api/patients/book")
                        .header("Authorization", MOCK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("appt_001"))
                .andExpect(jsonPath("$.doctorFirstName").value("Gregory"));
    }

    @Test
    void cancelAppointment_ShouldReturnNoContent() throws Exception {
        log.info("Testing endpoint: DELETE /api/patients/cancel/{appointment_id}");

        mockMvc.perform(delete("/api/patients/cancel/appt_001")
                        .header("Authorization", MOCK_TOKEN))
                .andExpect(status().isNoContent());

        verify(patientService).cancelAppointment("appt_001");
    }

    @Test
    void getMyAppointments_ShouldReturnPaginatedAppointments() throws Exception {
        log.info("Testing endpoint: GET /api/patients/my-appointments");
        AppointmentPatientDTO apptDTO = new AppointmentPatientDTO();
        apptDTO.setId("appt_001");

        when(patientService.getAppointmentsByEmail(eq(MOCK_EMAIL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(apptDTO)));

        mockMvc.perform(get("/api/patients/my-appointments")
                        .header("Authorization", MOCK_TOKEN)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("appt_001"));
    }

    @Test
    void getMySymptomReports_ShouldReturnPaginatedReports() throws Exception {
        log.info("Testing endpoint: GET /api/patients/symptomreports");
        SymptomReportBriefDTO reportDTO = new SymptomReportBriefDTO();
        reportDTO.setContext("Fever and chills");

        when(patientService.getSymptomReportsByEmail(eq(MOCK_EMAIL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reportDTO)));

        mockMvc.perform(get("/api/patients/symptomreports")
                        .header("Authorization", MOCK_TOKEN)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].context").value("Fever and chills"));
    }

    @Test
    void createSymptomReportByEmail_ShouldReturnCreatedReport() throws Exception {
        log.info("Testing endpoint: POST /api/patients/symptomsreport");
        SymptomReportCreateDTO createDTO = new SymptomReportCreateDTO();
        createDTO.setContext("Severe cough");

        SymptomReportBriefDTO resultDTO = new SymptomReportBriefDTO();
        resultDTO.setContext("Severe cough");

        when(patientService.createSymptomReportByEmail(eq(MOCK_EMAIL), any(SymptomReportCreateDTO.class))).thenReturn(resultDTO);

        mockMvc.perform(post("/api/patients/symptomsreport")
                        .header("Authorization", MOCK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context").value("Severe cough"));
    }

    @Test
    void addRating_ShouldReturnSavedRating() throws Exception {
        log.info("Testing endpoint: POST /api/patients/rating");
        RatingDTO ratingDTO = new RatingDTO();
        ratingDTO.setDoctorNpi("NPI-12345");
        ratingDTO.setRating(5);

        when(patientService.addRatingByEmail(eq(MOCK_EMAIL), any(RatingDTO.class))).thenReturn(ratingDTO);

        mockMvc.perform(post("/api/patients/rating")
                        .header("Authorization", MOCK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ratingDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorNpi").value("NPI-12345"))
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void getAllRatings_ShouldReturnPaginatedRatings() throws Exception {
        log.info("Testing endpoint: GET /api/patients/ratings");
        RatingDTO ratingDTO = new RatingDTO();
        ratingDTO.setRating(4);

        when(patientService.getAllRatingsByEmail(eq(MOCK_EMAIL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ratingDTO)));

        mockMvc.perform(get("/api/patients/ratings")
                        .header("Authorization", MOCK_TOKEN)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rating").value(4));
    }

    @Test
    void findSpecialistsInCity_ShouldReturnSpecialistsList() throws Exception {
        log.info("Testing endpoint: GET /api/patients/search/specialists/{city}");
        SpecialistDTO specialist = new SpecialistDTO();
        // Utilizziamo i campi reali esposti dal tuo DTO
        specialist.setNpi("NPI-555");
        specialist.setFirstName("Gregory");
        specialist.setLastName("House");

        when(patientService.findSpecialistsByDiagnosisAndCity("Milan", "Hypertension"))
                .thenReturn(List.of(specialist));

        mockMvc.perform(get("/api/patients/search/specialists/Milan")
                        .header("Authorization", MOCK_TOKEN)
                        .param("diagnosis", "Hypertension"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].npi").value("NPI-555"))
                .andExpect(jsonPath("$[0].firstName").value("Gregory"))
                .andExpect(jsonPath("$[0].lastName").value("House"));

        log.debug("Successfully validated specialist search response mapping.");
    }

    @Test
    void getDoctorDetails_ShouldReturnDoctorSlots() throws Exception {
        log.info("Testing endpoint: GET /api/patients/doctor/{npi}");
        DoctorReadSlotsDTO doctorDTO = new DoctorReadSlotsDTO();
        doctorDTO.setNpi("NPI-98765");
        doctorDTO.setFirstName("House");

        when(patientService.getDoctorByNpi("NPI-98765")).thenReturn(doctorDTO);

        mockMvc.perform(get("/api/patients/doctor/NPI-98765")
                        .header("Authorization", MOCK_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("House"));
    }
}