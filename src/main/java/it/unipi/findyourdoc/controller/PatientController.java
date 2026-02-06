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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing Registered Patients.
 *
 * <p>Provides endpoints for registration, profile updates, retrieval, deletion, search, and
 * bookmark management.
 */
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Tag(
        name = "Patient Management",
        description =
                "Operations related to registered patients, including authentication context and search.")
public class PatientController {

    private final PatientService patientService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(
            summary = "Register a new Patient",
            description = "Creates a new patient account with the provided details.")
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Patient registered successfully",
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
            summary = "Update Patient Profile",
            description =
                    "Updates email, full name or password. Requires 'PATIENT'")
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200", description = "Patient updated successfully"),
                    @ApiResponse(responseCode = "404", description = "Patient not found"),
                    @ApiResponse(responseCode = "400", description = "Duplicate email or invalid data")
            })
    @PutMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientReadDTO> updatePatient(HttpServletRequest request,
            @RequestBody PatientUpdateDTO updateDTO) {
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);
        return ResponseEntity.ok(patientService.updatePatient(email, updateDTO));
    }


    @Operation(
            summary = "Get Current Patient Profile",
            description = "Retrieves the full profile of the currently logged-in patient using the JWT token."
    )
    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')") // Assicuriamoci che sia un paziente
    public ResponseEntity<PatientReadDTO> getCurrentPatientProfile(HttpServletRequest request) {

        // 1. Estrazione Email dal Token (Identity)
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        // 2. Recupero Dati Completi dal Service (Data Fetching)
        // Questo metodo (getUserByEmail) interroga MongoDB, mappa l'Entity in DTO
        // e restituisce tutti i campi definiti nel tuo PatientReadDTO.
        PatientReadDTO patientProfile = patientService.getPatientByEmail(email);

        // 3. Ritorno al Client
        return ResponseEntity.ok(patientProfile);
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
    public ResponseEntity<Page<AppointmentPatientDTO>> getMyAppointments(
            HttpServletRequest request,
            @ParameterObject
            @PageableDefault(size = 10, sort = "dateTime") Pageable pageable) { // <--- STILE ADMIN

        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);
        return ResponseEntity.ok(patientService.getAppointmentsByEmail(email, pageable));
    }

    @Operation(
            summary = "Get all symptom reports (Paginated)",
            description = "Retrieves paginated reports. Default: Sorted by createdAt DESC.")
    @GetMapping("/symptomreports")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Page<SymptomReportBriefDTO>> getMySymptomReports(
            HttpServletRequest request,
            @ParameterObject
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {

        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        return ResponseEntity.ok(patientService.getSymptomReportsByEmail(email, pageable));
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

    @Operation(
            summary = "View all ratings (Paginated)",
            description = "Retrieves all ratings. Default: size 20.")
    @GetMapping("/ratings")
    public ResponseEntity<Page<RatingDTO>> getAllRatings(
            HttpServletRequest request,
            @ParameterObject
            @PageableDefault(size = 20) Pageable pageable) {

        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        return ResponseEntity.ok(patientService.getAllRatingsByEmail(email, pageable));
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
