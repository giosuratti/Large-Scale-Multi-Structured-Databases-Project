package it.unipi.findyourdoc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.security.JwtTokenProvider;
import it.unipi.findyourdoc.service.DoctorService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
        String email = jwtTokenProvider.getEmailFromToken(token); // Il subject del token è la mail

        Map<String, String> doctorInfo = new HashMap<>();
        doctorInfo.put("email", email);

        return ResponseEntity.ok(doctorInfo);
    }

    @Operation(
            summary = "Get all appointments for the current doctor",
            description = "Retrieves the list of all appointments associated with the authenticated doctor.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Agenda retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/my-appointments")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<AppointmentDTO>> getMyAgenda(HttpServletRequest request) {
        // Estraiamo l'email dal token JWT
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        // Chiamiamo il service per recuperare gli appuntamenti
        return ResponseEntity.ok(doctorService.getAppointmentsByEmail(email));
    }


    @Operation(summary = "Get all ratings for a specific doctor",
            description = "Public endpoint to view all feedback for a doctor.")
    @GetMapping("/ratings")
    public ResponseEntity<List<RatingDTO>> getDoctorRatings(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);
        return ResponseEntity.ok(doctorService.getRatingsByDoctorEmail(email));
    }

    @Operation(summary = "Add availability slots",
            description = "Allows the authenticated doctor to add new time slots for appointments.")
    @PostMapping("/slots")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> addSlots(HttpServletRequest request, @RequestBody List<SlotDTO> slots) {
        // Estraiamo l'email dal token per essere sicuri di chi sta aggiungendo gli slot
        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));

        doctorService.addAvailabilitySlots(email, slots);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Remove an availability slot",
            description = "Allows the authenticated doctor to remove a specific time slot from their schedule.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Slot removed successfully"),
            @ApiResponse(responseCode = "404", description = "Doctor or Slot not found")
    })
    @DeleteMapping("/slots")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> removeSlot(HttpServletRequest request, @RequestBody SlotDTO slotDTO) {
        // Estraiamo l'email dal token per sicurezza
        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));

        doctorService.removeAvailabilitySlot(email, slotDTO);
        return ResponseEntity.noContent().build(); // 204 No Content è lo standard per le cancellazioni
    }

}