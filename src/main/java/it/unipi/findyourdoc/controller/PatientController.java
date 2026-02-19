package it.unipi.findyourdoc.controller;

import io.swagger.v3.oas.annotations.Operation;
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
 * REST controller for Patient operations.
 * Manages identity, health reports, booking logic, and medical search.
 */
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
@Tag(
        name = "Patient Management",
        description = "Operations related to registered patients, including authentication context and search.")
public class PatientController {

    private final PatientService patientService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Public endpoint for new patient onboarding.
     */
    @Operation(summary = "Register a new Patient")
    @PostMapping("/register")
    public ResponseEntity<PatientReadDTO> registerPatient(@RequestBody PatientCreateDTO createDTO) {
        return ResponseEntity.ok(patientService.registerPatient(createDTO));
    }

    /**
     * Updates personal profile data.
     * Subject is extracted from JWT for identity verification.
     */
    @Operation(summary = "Update Patient Profile")
    @PutMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientReadDTO> updatePatient(HttpServletRequest request,
                                                        @RequestBody PatientUpdateDTO updateDTO) {
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);
        return ResponseEntity.ok(patientService.updatePatient(email, updateDTO));
    }

    /**
     * Retrieves the current authenticated patient's profile.
     */
    @Operation(summary = "Get Current Patient Profile")
    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientReadDTO> getCurrentPatientProfile(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);
        return ResponseEntity.ok(patientService.getPatientByEmail(email));
    }

    /**
     * Initiates the appointment booking process.
     */
    @Operation(summary = "Let a patient book a visit")
    @PostMapping("/book")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentPatientDTO> bookAppointmentById(HttpServletRequest request, @RequestBody AppointmentBookDTO appointmentBookDTO) {
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);
        return ResponseEntity.ok(patientService.bookAppointmentByEmail(email, appointmentBookDTO));
    }

    /**
     * Cancels an existing appointment.
     */
    @Operation(summary = "Cancel a booked visit")
    @DeleteMapping("/cancel/{appointment_id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Void> cancelAppointment(@PathVariable String appointment_id) {
        patientService.cancelAppointment(appointment_id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns a paginated list of upcoming appointments for the caller.
     */
    @Operation(summary = "Get all future appointments for the current patient")
    @GetMapping("/my-appointments")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Page<AppointmentPatientDTO>> getMyAppointments(
            HttpServletRequest request,
            @ParameterObject @PageableDefault(size = 10, sort = "dateTime") Pageable pageable) {

        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);
        return ResponseEntity.ok(patientService.getAppointmentsByEmail(email, pageable));
    }

    /**
     * Retrieves patient-specific symptom reports chronologically.
     */
    @Operation(summary = "Get all symptom reports (Paginated)")
    @GetMapping("/symptomreports")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Page<SymptomReportBriefDTO>> getMySymptomReports(
            HttpServletRequest request,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {

        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        return ResponseEntity.ok(patientService.getSymptomReportsByEmail(email, pageable));
    }

    /**
     * Submits a new symptom report.
     */
    @Operation(summary = "Create symptom report")
    @PostMapping("/symptomsreport")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<SymptomReportBriefDTO> createSymptomReportByEmail(
            HttpServletRequest request,
            @RequestBody SymptomReportCreateDTO reportDTO) {

        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        return ResponseEntity.ok(patientService.createSymptomReportByEmail(email, reportDTO));
    }

    /**
     * Allows patients to rate doctors after consultation.
     */
    @Operation(summary = "Rate a doctor")
    @PostMapping("/rating")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<RatingDTO> addRating(HttpServletRequest request, @RequestBody @Valid RatingDTO ratingDTO) {
        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        return ResponseEntity.ok(patientService.addRatingByEmail(email, ratingDTO));
    }

    /**
     * Lists all ratings submitted by the current patient.
     */
    @Operation(summary = "View all ratings (Paginated)")
    @GetMapping("/ratings")
    public ResponseEntity<Page<RatingDTO>> getAllRatings(
            HttpServletRequest request,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        return ResponseEntity.ok(patientService.getAllRatingsByEmail(email, pageable));
    }

    /**
     * Bridge endpoint: Maps a diagnosis to a specialty in Neo4j and searches Doctors in MongoDB.
     */
    @Operation(summary = "Find specialists by diagnosis and city")
    @GetMapping("/search/specialists/{city}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<SpecialistDTO>> findSpecialistsInCity(
            @PathVariable String city,
            @RequestParam String diagnosis) {
        return ResponseEntity.ok(patientService.findSpecialistsByDiagnosisAndCity(city, diagnosis));
    }

    /**
     * Retrieves full doctor details including real-time availability slots.
     */
    @Operation(summary = "Get Doctor Details")
    @GetMapping("/doctor/{npi}")
    @PreAuthorize("hasAnyRole('PATIENT', 'ADMIN')")
    public ResponseEntity<DoctorReadSlotsDTO> getDoctorDetails(@PathVariable String npi) {
        return ResponseEntity.ok(patientService.getDoctorByNpi(npi));
    }
}