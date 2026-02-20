package it.unipi.findyourdoc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unipi.findyourdoc.dto.auth.AuthResponseDTO;
import it.unipi.findyourdoc.dto.auth.LoginRequestDTO;
import it.unipi.findyourdoc.security.JwtTokenFilter;
import it.unipi.findyourdoc.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for the AuthController.
 * Validates endpoint routing and JSON serialization for the authentication process.
 * Security filters are intentionally bypassed to focus on the controller's logic.
 */
@WebMvcTest(
        // Disable JwtTokenFilter security checks at scan time
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtTokenFilter.class}
        )
)
// Disable all (security) filters at runtime
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    private static final Logger log = LoggerFactory.getLogger(AuthControllerTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    private LoginRequestDTO mockLoginRequest;
    private AuthResponseDTO mockAuthResponse;

    @BeforeEach
    void setUp() {
        log.debug("Initializing mock authentication data.");

        mockLoginRequest = new LoginRequestDTO();
        mockLoginRequest.setEmail("user@findyourdoc.it");
        mockLoginRequest.setPassword("securePassword123");

        mockAuthResponse = new AuthResponseDTO();
        mockAuthResponse.setAccessToken("mock.jwt.token.string");
        mockAuthResponse.setEmail("user@findyourdoc.it");
    }

    @Test
    void loginAdmin_ShouldReturnAuthResponse() throws Exception {
        log.info("Testing endpoint: POST /api/auth/admin/login");

        mockAuthResponse.setRole("ADMIN");
        when(authService.loginAdmin(any(LoginRequestDTO.class))).thenReturn(mockAuthResponse);

        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock.jwt.token.string"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.email").value("user@findyourdoc.it"));

        verify(authService).loginAdmin(any(LoginRequestDTO.class));
        log.debug("Validated successful Admin login mapping.");
    }

    @Test
    void loginDoctor_ShouldReturnAuthResponse() throws Exception {
        log.info("Testing endpoint: POST /api/auth/doctor/login");

        mockAuthResponse.setRole("DOCTOR");
        when(authService.loginDoctor(any(LoginRequestDTO.class))).thenReturn(mockAuthResponse);

        mockMvc.perform(post("/api/auth/doctor/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock.jwt.token.string"))
                .andExpect(jsonPath("$.role").value("DOCTOR"));

        verify(authService).loginDoctor(any(LoginRequestDTO.class));
        log.debug("Validated successful Doctor login mapping.");
    }

    @Test
    void loginPatient_ShouldReturnAuthResponse() throws Exception {
        log.info("Testing endpoint: POST /api/auth/patient/login");

        mockAuthResponse.setRole("PATIENT");
        when(authService.loginPatient(any(LoginRequestDTO.class))).thenReturn(mockAuthResponse);

        mockMvc.perform(post("/api/auth/patient/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock.jwt.token.string"))
                .andExpect(jsonPath("$.role").value("PATIENT"));

        verify(authService).loginPatient(any(LoginRequestDTO.class));
        log.debug("Validated successful Patient login mapping.");
    }
}