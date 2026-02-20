package it.unipi.findyourdoc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.security.JwtTokenProvider;
import it.unipi.findyourdoc.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for the AdminController using MockMvc.
 * Security filters are bypassed to isolate and test the web layer routing and serialization.
 */
@WebMvcTest(AdminController.class) // Load only AdminController.class implementations
@AutoConfigureMockMvc(addFilters = false) // Disabled login checks
class AdminControllerTest {

    private static final Logger log = LoggerFactory.getLogger(AdminControllerTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminService adminService;

    // Required to successfully load the ApplicationContext since it's injected in the controller's constructor
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private final String MOCK_TOKEN = "Bearer mock.jwt.token";
    private final String MOCK_EMAIL = "admin@findyourdoc.it";

    @BeforeEach
    void setUp() {
        // Default mock behavior for JWT extraction used in /me endpoints
        // These are the only methods not belonging to AdminController.class which are "implementend" (we define do not define their logic, but only their return).
        when(jwtTokenProvider.resolveToken(any())).thenReturn(MOCK_TOKEN); // Regardless of its input, the resolveToken shall return MOCK_TOCKEN
        when(jwtTokenProvider.getEmailFromToken(MOCK_TOKEN)).thenReturn(MOCK_EMAIL);
        log.debug("Test context initialized with mock JWT data.");
    }

    @Test
    void createAdmin_ShouldReturnOk() throws Exception {
        log.info("Testing endpoint: POST /api/admins");
        AdminCreateDTO createDTO = new AdminCreateDTO();
        createDTO.setEmail("newadmin@findyourdoc.it");
        createDTO.setPassword("securePwd123");

        AdminReadDTO readDTO = new AdminReadDTO();
        readDTO.setEmail("newadmin@findyourdoc.it");

        when(adminService.createAdmin(any(AdminCreateDTO.class))).thenReturn(readDTO);

        mockMvc.perform(post("/api/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDTO)))
                .andExpect(status().isOk()) // Controller returns ResponseEntity.ok()
                .andExpect(jsonPath("$.email").value("newadmin@findyourdoc.it"));
    }

    @Test
    void updateAdmin_ShouldReturnOk() throws Exception {
        log.info("Testing endpoint: PUT /api/admins/me");
        AdminUpdateDTO updateDTO = new AdminUpdateDTO();
        updateDTO.setTelephone("555-12345");

        AdminReadDTO readDTO = new AdminReadDTO();
        readDTO.setEmail(MOCK_EMAIL);
        readDTO.setTelephone("555-12345");

        when(adminService.updateAdmin(eq(MOCK_EMAIL), any(AdminUpdateDTO.class))).thenReturn(readDTO);

        mockMvc.perform(put("/api/admins/me")
                        .header("Authorization", MOCK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telephone").value("555-12345"));
    }

    @Test
    void getAdminMe_ShouldReturnOk() throws Exception {
        log.info("Testing endpoint: GET /api/admins/me");
        AdminReadDTO readDTO = new AdminReadDTO();
        readDTO.setEmail(MOCK_EMAIL);

        when(adminService.getAdminByEmail(MOCK_EMAIL)).thenReturn(readDTO);

        mockMvc.perform(get("/api/admins/me")
                        .header("Authorization", MOCK_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(MOCK_EMAIL));
    }

    @Test
    void getAllAdmins_ShouldReturnPaginatedList() throws Exception {
        log.info("Testing endpoint: GET /api/admins");
        AdminReadDTO readDTO = new AdminReadDTO();
        readDTO.setEmail(MOCK_EMAIL);

        when(adminService.getAllAdmins(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(readDTO)));

        mockMvc.perform(get("/api/admins")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value(MOCK_EMAIL));
    }

    @Test
    void searchAdmins_ShouldReturnList() throws Exception {
        log.info("Testing endpoint: GET /api/admins/search?email=...");
        AdminReadDTO readDTO = new AdminReadDTO();
        readDTO.setEmail("admintest@findyourdoc.it");

        when(adminService.searchAdmins("admintest")).thenReturn(List.of(readDTO));

        // Note: the controller expects @RequestParam String email, not emailPrefix
        mockMvc.perform(get("/api/admins/search")
                        .param("email", "admintest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("admintest@findyourdoc.it"));
    }

    @Test
    void deleteAdmin_ShouldReturnNoContent() throws Exception {
        log.info("Testing endpoint: DELETE /api/admins/ (Email in RequestBody)");
        String targetEmail = "user@test.it";

        // Controller expects the email directly in the body as a String
        mockMvc.perform(delete("/api/admins/")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(targetEmail))
                .andExpect(status().isNoContent());

        verify(adminService).deleteUser(targetEmail);
        log.debug("Verified adminService.deleteUser was called with {}", targetEmail);
    }

    @Test
    void forcePasswordChange_ShouldReturnOk() throws Exception {
        log.info("Testing endpoint: PATCH /api/admins/accounts/{id}/password");
        PasswordChangeDTO passwordDTO = new PasswordChangeDTO();
        passwordDTO.setNewPassword("newSecr3t!!!");

        mockMvc.perform(patch("/api/admins/accounts/user123/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(passwordDTO)))
                .andExpect(status().isOk())
                .andExpect(content().string("Password successfully updated for user ID: user123"));

        verify(adminService).changeUserPassword("user123", "newSecr3t!!!");
    }

    @Test
    void syncRatings_ShouldReturnOkMessage() throws Exception {
        log.info("Testing endpoint: POST /api/admins/sync-ratings");

        // Simulating the synchronization trigger.
        // Note: In a live environment, Redis handles the underlying cache invalidations for these ratings.
        mockMvc.perform(post("/api/admins/sync-ratings"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Synchronization completed")));

        verify(adminService).syncDoctorRatings();
    }

    @Test
    void registerDoctor_ShouldReturnOk() throws Exception {
        log.info("Testing endpoint: POST /api/admins/registerdoctor");
        DoctorCreateDTO doctorDTO = new DoctorCreateDTO();
        doctorDTO.setEmail("doctor@findyourdoc.it");

        DoctorReadDTO readDTO = new DoctorReadDTO();
        readDTO.setEmail("doctor@findyourdoc.it");

        when(adminService.registerDoctor(any(DoctorCreateDTO.class))).thenReturn(readDTO);

        mockMvc.perform(post("/api/admins/registerdoctor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(doctorDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("doctor@findyourdoc.it"));
    }

    @Test
    void syncAllDoctors_ShouldReturnOkMessage() throws Exception {
        log.info("Testing endpoint: POST /api/admins/sync/all-doctors");

        mockMvc.perform(post("/api/admins/sync/all-doctors"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Batch synchronization completed")));

        verify(adminService).syncAllDoctors();
    }
}