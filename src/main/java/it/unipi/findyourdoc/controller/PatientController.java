package it.unipi.findyourdoc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.dto.neo4j.SpecialistDTO;
import it.unipi.findyourdoc.security.JwtTokenProvider;
import it.unipi.findyourdoc.service.PatientService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            summary = "Let a patient book a visit",
            description = "Let a patient book a visit.")
    @PostMapping("/book")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentPatientDTO> bookAppointmentById(HttpServletRequest request, @RequestBody AppointmentDTO appointmentDTO) {
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);
        return ResponseEntity.ok(patientService.bookAppointmentByEmail(email, appointmentDTO));
    }

    @Operation(
            summary = "Cancel a booked visit",
            description = "Allows a patient to cancel an existing appointment. Usually changes status to 'CANCELLED'.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Appointment cancelled successfully"),
            @ApiResponse(responseCode = "404", description = "Appointment not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden - You can only cancel your own appointments")
    })
    @DeleteMapping("/cancel/{appointment_id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Void> cancelAppointment(@Parameter(description = "ID of the appointment to cancel") @PathVariable String appointment_id) {
        patientService.cancelAppointment(appointment_id);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Get all future appointments for the current patient",
            description = "Retrieves the list of all appointments associated with the authenticated patient.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of appointments retrieved successfully")
    })
    @GetMapping("/my-appointments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<AppointmentPatientDTO>> getMyAppointments(HttpServletRequest request) {
        // Estraiamo l'email dal token per identificare il paziente
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        return ResponseEntity.ok(patientService.getAppointmentsByEmail(email));
    }

    @Operation(summary = "Get all symptom reports", description = "Retrieves all reports for the authenticated patient.")
    @GetMapping("/symptomreports")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<SymptomReportBriefDTO>> getMySymptomReports(HttpServletRequest request) {
        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        return ResponseEntity.ok(patientService.getSymptomReportsByEmail(email));
    }

    @Operation(summary = "Create symptom report", description = "Creates a new report and calculates patient context (age/location).")
    @PostMapping("/symptomsreport")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<SymptomReportBriefDTO> createSymptomReportByEmail(
            HttpServletRequest request,
            @RequestBody SymptomReportCreateDTO reportDTO) {

        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        return ResponseEntity.ok(patientService.createSymptomReportByEmail(email, reportDTO));
    }

    @Operation(summary = "Rate a doctor", description = "Adds a rating to a doctor. The system automatically retrieves doctor's names.")
    @PostMapping("/rating")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<RatingDTO> addRating(HttpServletRequest request, @RequestBody @Valid RatingDTO ratingDTO) {
        // Passiamo al service l'ID del dottore e il voto
        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        return ResponseEntity.ok(patientService.addRatingByEmail(email, ratingDTO));
    }

    @Operation(summary = "View all ratings", description = "Retrieves all ratings stored in the system.")
    @GetMapping("/ratings")
    public ResponseEntity<List<RatingDTO>> getAllRatings(HttpServletRequest request) {
        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        return ResponseEntity.ok(patientService.getAllRatingsByEmail(email));
    }

    @Operation(
            summary = "Find specialists by diagnosis and city",
            description = "Maps a diagnosis to a specialty and searches for doctors in the specified city.")
    @GetMapping("/search/specialists/{city}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<SpecialistDTO>> findSpecialistsInCity(
            @Parameter(description = "The city to search in") @PathVariable String city,
            @Parameter(description = "The diagnosis (e.g. Flu, Arrhythmia)") @RequestParam String diagnosis) {

        // Non abbiamo più bisogno dell'email dal token per la posizione!
        return ResponseEntity.ok(patientService.findSpecialistsByDiagnosisAndCity(city, diagnosis));
    }

}
