package it.unipi.findyourdoc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.security.JwtTokenFilter;
import it.unipi.findyourdoc.security.JwtTokenProvider;
import it.unipi.findyourdoc.service.DoctorService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for the DoctorController using MockMvc.
 * Security filters are explicitly excluded to isolate the web layer routing,
 * request parsing, and response serialization.
 */
@WebMvcTest(
        controllers = DoctorController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtTokenFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class DoctorControllerTest {

    private static final Logger log = LoggerFactory.getLogger(DoctorControllerTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DoctorService doctorService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private final String MOCK_TOKEN = "Bearer mock.jwt.token";
    private final String MOCK_EMAIL = "doctor@findyourdoc.it";

    @BeforeEach
    void setUp() {
        log.debug("Initializing test context with mock JWT configurations.");
        when(jwtTokenProvider.resolveToken(any())).thenReturn(MOCK_TOKEN);
        when(jwtTokenProvider.getEmailFromToken(MOCK_TOKEN)).thenReturn(MOCK_EMAIL);
    }

    @Test
    void updateLocation_ShouldReturnUpdatedDoctor() throws Exception {
        log.info("Testing endpoint: PUT /api/doctors/me/location");
        LocationDTO locationDTO = new LocationDTO();
        locationDTO.setCity("Rome");
        // Populating all mandatory fields to pass @Valid checks
        locationDTO.setAddress("Via Roma, 1");
        locationDTO.setState("Lazio");
        locationDTO.setZipCode("00100");

        DoctorReadDTO readDTO = new DoctorReadDTO();
        readDTO.setEmail(MOCK_EMAIL);

        when(doctorService.updateDoctorLocation(eq(MOCK_EMAIL), any(LocationDTO.class))).thenReturn(readDTO);

        mockMvc.perform(put("/api/doctors/me/location")
                        .header("Authorization", MOCK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locationDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(MOCK_EMAIL));

        log.debug("Successfully validated location update with complete DTO payload.");
    }

    @Test
    void updatePassword_ShouldReturnOk() throws Exception {
        log.info("Testing endpoint: PUT /api/doctors/me/password");
        PasswordChangeDTO passwordDTO = new PasswordChangeDTO();
        passwordDTO.setNewPassword("newSecurePass123!");

        mockMvc.perform(put("/api/doctors/me/password")
                        .header("Authorization", MOCK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordDTO)))
                .andExpect(status().isOk());

        verify(doctorService).updateDoctorPassword(MOCK_EMAIL, passwordDTO);
    }

    @Test
    void updatePhone_ShouldReturnUpdatedDoctor() throws Exception {
        log.info("Testing endpoint: PUT /api/doctors/me/phone");
        TelephoneUpdateDTO phoneDTO = new TelephoneUpdateDTO();
        phoneDTO.setTelephone("333-1234567");

        DoctorReadDTO readDTO = new DoctorReadDTO();
        readDTO.setEmail(MOCK_EMAIL);
        readDTO.setTelephone("333-1234567");

        when(doctorService.updateDoctorPhone(eq(MOCK_EMAIL), any(TelephoneUpdateDTO.class))).thenReturn(readDTO);

        mockMvc.perform(put("/api/doctors/me/phone")
                        .header("Authorization", MOCK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(phoneDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telephone").value("333-1234567"));
    }

    @Test
    void getCurrentDoctorInfo_ShouldReturnProfile() throws Exception {
        log.info("Testing endpoint: GET /api/doctors/me");
        DoctorReadDTO readDTO = new DoctorReadDTO();
        readDTO.setEmail(MOCK_EMAIL);
        readDTO.setFirstName("John");

        when(doctorService.getDoctorByEmail(MOCK_EMAIL)).thenReturn(readDTO);

        mockMvc.perform(get("/api/doctors/me")
                        .header("Authorization", MOCK_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("John"));
    }

    @Test
    void getMyAgenda_ShouldReturnAppointmentsList() throws Exception {
        log.info("Testing endpoint: GET /api/doctors/my-appointments");
        AppointmentFullDTO apptDTO = new AppointmentFullDTO();
        apptDTO.setId("appt123");

        when(doctorService.getAppointmentsByEmail(MOCK_EMAIL)).thenReturn(List.of(apptDTO));

        mockMvc.perform(get("/api/doctors/my-appointments")
                        .header("Authorization", MOCK_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("appt123"));
    }

    @Test
    void getDoctorRatings_ShouldReturnPaginatedRatings() throws Exception {
        log.info("Testing endpoint: GET /api/doctors/ratings");

        when(doctorService.getRatingsByDoctorEmail(eq(MOCK_EMAIL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(5, 4, 5)));

        mockMvc.perform(get("/api/doctors/ratings")
                        .header("Authorization", MOCK_TOKEN)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0]").value(5));
    }

    @Test
    void addSlots_ShouldReturnCreated() throws Exception {
        log.info("Testing endpoint: POST /api/doctors/slots");
        SlotDTO slotDTO = new SlotDTO();
        slotDTO.setDateTime(LocalDateTime.now().plusDays(1));

        // Note: The MockMvc layer tests the HTTP boundary.
        // The underlying service delegates the high-speed slot locking and cache invalidation to the Redis cluster.
        mockMvc.perform(post("/api/doctors/slots")
                        .header("Authorization", MOCK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(slotDTO))))
                .andExpect(status().isCreated());

        verify(doctorService).addAvailabilitySlots(eq(MOCK_EMAIL), any(List.class));
    }

    @Test
    void removeSlot_ShouldReturnNoContent() throws Exception {
        log.info("Testing endpoint: DELETE /api/doctors/slots");
        SlotDTO slotDTO = new SlotDTO();
        slotDTO.setDateTime(LocalDateTime.now().plusDays(2));

        mockMvc.perform(delete("/api/doctors/slots")
                        .header("Authorization", MOCK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(slotDTO)))
                .andExpect(status().isNoContent());

        verify(doctorService).removeAvailabilitySlot(eq(MOCK_EMAIL), any(SlotDTO.class));
    }

    @Test
    void getPatientSymptomReports_ShouldReturnPaginatedReports() throws Exception {
        log.info("Testing endpoint: GET /api/doctors/patient/{patientId}/symptom-reports");
        SymptomReportBriefDTO reportDTO = new SymptomReportBriefDTO();
        reportDTO.setContext("Patient reported severe headache.");

        when(doctorService.getPatientSymptomReports(eq("pat123"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reportDTO)));

        mockMvc.perform(get("/api/doctors/patient/pat123/symptom-reports")
                        .header("Authorization", MOCK_TOKEN)
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].context").value("Patient reported severe headache."));
    }
}