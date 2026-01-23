package it.unipi.findyourdoc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unipi.findyourdoc.dto.auth.LoginRequestDTO; // Sostituisce LoginRequestDTO
import it.unipi.findyourdoc.dto.auth.AuthResponseDTO; // Sostituisce AuthResponseDTO
import it.unipi.findyourdoc.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller responsible for handling authentication requests for FindYourDoc.
 * Provides public endpoints for login sessions for Admins, Doctors, and Patients.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication API", description = "Endpoints for managing secure login sessions")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Admin Login", description = "Authenticates an administrator. Returns a JWT token with ADMIN privileges.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "400", description = "Malformed request body")
    })
    @PostMapping("/admin/login")
    public ResponseEntity<AuthResponseDTO> loginAdmin(@RequestBody LoginRequestDTO loginRequest) {
        log.info("Received login request for ADMIN: {}", loginRequest.getEmail());
        return ResponseEntity.ok(authService.loginAdmin(loginRequest));
    }

    @Operation(summary = "Doctor Login", description = "Authenticates a medical professional. Returns a JWT token with DOCTOR privileges.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/doctor/login")
    public ResponseEntity<AuthResponseDTO> loginDoctor(@RequestBody LoginRequestDTO loginRequest) {
        log.info("Received login request for DOCTOR: {}", loginRequest.getEmail());
        return ResponseEntity.ok(authService.loginDoctor(loginRequest));
    }

    @Operation(summary = "Patient Login", description = "Authenticates a patient. Returns a JWT token with PATIENT privileges.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/patient/login")
    public ResponseEntity<AuthResponseDTO> loginPatient(@RequestBody LoginRequestDTO loginRequest) {
        log.info("Received login request for PATIENT: {}", loginRequest.getEmail());
        return ResponseEntity.ok(authService.loginPatient(loginRequest));
    }
}
