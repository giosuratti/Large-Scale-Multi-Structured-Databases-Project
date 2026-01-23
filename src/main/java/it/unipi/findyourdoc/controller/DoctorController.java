package it.unipi.findyourdoc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unipi.findyourdoc.dto.mongo.DoctorReadDTO;
import it.unipi.findyourdoc.dto.mongo.DoctorCreateDTO;
import it.unipi.findyourdoc.dto.mongo.DoctorUpdateDTO;
import it.unipi.findyourdoc.security.JwtTokenProvider;
import it.unipi.findyourdoc.service.DoctorService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for managing Medical Doctors.
 * Provides endpoints for registration, profile management, and retrieval.
 */
@RestController
@RequestMapping("/api/doctors")
@RequiredArgsConstructor
@Tag(
        name = "Doctor Management",
        description = "Operations related to medical professionals, including profile management and lookup.")
public class DoctorController {

    private final DoctorService doctorService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(
            summary = "Register a new Doctor",
            description = "Creates a new doctor account with professional details.")
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Doctor registered successfully",
                            content = @Content(schema = @Schema(implementation = DoctorReadDTO.class))),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid input or email already exists")
            })
    @PostMapping("/register")
    public ResponseEntity<DoctorReadDTO> registerDoctor(
            @RequestBody DoctorCreateDTO createDTO) {
        return ResponseEntity.ok(doctorService.registerDoctor(createDTO));
    }

    @Operation(
            summary = "Update Doctor Profile",
            description = "Updates professional information or credentials. Requires 'DOCTOR' role.")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
                    @ApiResponse(responseCode = "404", description = "Doctor not found"),
                    @ApiResponse(responseCode = "403", description = "Forbidden")
            })
    @PutMapping("/{email}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorReadDTO> updateDoctor(
            @Parameter(description = "Email of the doctor to update") @PathVariable String email,
            @RequestBody DoctorUpdateDTO updateDTO) {
        return ResponseEntity.ok(doctorService.updateDoctor(email, updateDTO));
    }

    @Operation(
            summary = "Get Current Doctor Info",
            description = "Extracts professional email directly from the JWT Token context.")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> getCurrentDoctorInfo(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getUsernameFromToken(token); // Il subject del token è la mail

        Map<String, String> doctorInfo = new HashMap<>();
        doctorInfo.put("email", email);

        return ResponseEntity.ok(doctorInfo);
    }

    @Operation(
            summary = "Get Doctor by email",
            description = "Retrieves detailed professional information for a specific doctor.")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "Doctor found"),
                    @ApiResponse(responseCode = "404", description = "Doctor not found")
            })
    @GetMapping("/{email}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DoctorReadDTO> getDoctorByEmail(
            @Parameter(description = "The email of the doctor to retrieve") @PathVariable String email) {
        return ResponseEntity.ok(doctorService.getDoctorByEmail(email));
    }
}