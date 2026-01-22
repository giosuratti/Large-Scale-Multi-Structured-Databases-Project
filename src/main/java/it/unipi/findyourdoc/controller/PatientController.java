package it.unipi.findyourdoc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unipi.findyourdoc.dto.mongo.PatientReadDTO;
import it.unipi.findyourdoc.dto.mongo.PatientCreateDTO;
import it.unipi.findyourdoc.dto.mongo.PatientUpdateDTO;
import it.unipi.findyourdoc.security.JwtTokenProvider;
import it.unipi.findyourdoc.service.PatientService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing Registered Users.
 *
 * <p>Provides endpoints for registration, profile updates, retrieval, deletion, search, and
 * bookmark management.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(
        name = "User Management",
        description =
                "Operations related to registered users, including authentication context and search.")
public class PatientController {

    private final PatientService patientService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(
            summary = "Register a new User",
            description = "Creates a new user account with the provided details.")
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "User registered successfully",
                            content = @Content(schema = @Schema(implementation = PatientCreateDTO.class))),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid input or duplicate username/email")
            })
    @PostMapping("/register")
    public ResponseEntity<PatientReadDTO> registerPatient(
            @RequestBody PatientCreateDTO createDTO) {
        return ResponseEntity.ok(patientService.registerPatient(createDTO));
    }

    @Operation(
            summary = "Update User Profile",
            description =
                    "Updates email, full name or password. Requires 'USER' or 'ADMIN' role.")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "User updated successfully"),
                    @ApiResponse(responseCode = "404", description = "User not found"),
                    @ApiResponse(responseCode = "400", description = "Duplicate email or invalid data")
            })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientReadDTO> updatePatient(
            @Parameter(description = "ID of the patient to update") @PathVariable String id,
            @RequestBody PatientUpdateDTO updateDTO) {
        return ResponseEntity.ok(patientService.updatePatient(id, updateDTO));
    }


    @Operation(
            summary = "Get Current Patient Info",
            description =
                    "Extracts email directly from the JWT Token without querying the database.")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> getCurrentPatientInfo(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);

        // Extract info from token
        String email = jwtTokenProvider.getEmailFromToken(token);

        Map<String, String> patientInfo = new HashMap<>();
        patientInfo.put("email", email);

        return ResponseEntity.ok(patientInfo);
    }

    @Operation(
            summary = "Get User by email",
            description = "Retrieves detailed information for a specific user.")
    @GetMapping("/{email}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PatientReadDTO> getUserByEmail(@PathVariable String email) {
        return ResponseEntity.ok(patientService.getUserByEmail(email));
    }

}
