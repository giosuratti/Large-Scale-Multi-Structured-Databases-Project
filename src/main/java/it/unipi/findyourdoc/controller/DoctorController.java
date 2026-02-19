package it.unipi.findyourdoc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.security.JwtTokenProvider;
import it.unipi.findyourdoc.service.DoctorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for medical professionals.
 * Handles specialized profile updates, availability scheduling, and clinical data access.
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

    /**
     * Updates the doctor's primary office location.
     * Identity is extracted from JWT for security.
     */
    @Operation(summary = "Update only the location")
    @PutMapping("/me/location")
    @PreAuthorize("hasAnyRole('DOCTOR')")
    public ResponseEntity<DoctorReadDTO> updateLocation(
            HttpServletRequest request,
            @RequestBody @Valid LocationDTO locationDTO) {

        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        return ResponseEntity.ok(doctorService.updateDoctorLocation(email, locationDTO));
    }

    /**
     * Self-service password update for medical staff.
     */
    @Operation(summary = "Update password")
    @PutMapping("/me/password")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<Void> updatePassword(HttpServletRequest request,
                                               @RequestBody PasswordChangeDTO newPassword) {
        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        doctorService.updateDoctorPassword(email, newPassword);
        return ResponseEntity.ok().build();
    }

    /**
     * Updates contact info. Triggers data-update flags for system-wide synchronization.
     */
    @Operation(summary = "Update phone number")
    @PutMapping("/me/phone")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<DoctorReadDTO> updatePhone(HttpServletRequest request,
                                                     @RequestBody TelephoneUpdateDTO phoneDTO) {
        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        return ResponseEntity.ok(doctorService.updateDoctorPhone(email, phoneDTO));
    }

    /**
     * Retrieves the complete professional profile of the authenticated doctor.
     */
    @Operation(summary = "Get Current Doctor Info")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DoctorReadDTO> getCurrentDoctorInfo(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        return ResponseEntity.ok(doctorService.getDoctorByEmail(email));
    }

    /**
     * Accesses the list of confirmed appointments for the doctor.
     */
    @Operation(summary = "Get all appointments for the current doctor")
    @GetMapping("/my-appointments")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<List<AppointmentFullDTO>> getMyAgenda(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        return ResponseEntity.ok(doctorService.getAppointmentsByEmail(email));
    }

    /**
     * Paginated retrieval of patient ratings for the doctor.
     */
    @Operation(summary = "Get all numeric ratings for a specific doctor")
    @GetMapping("/ratings")
    public ResponseEntity<Page<Integer>> getDoctorRatings(
            HttpServletRequest request,
            @ParameterObject Pageable pageable) {

        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        return ResponseEntity.ok(doctorService.getRatingsByDoctorEmail(email, pageable));
    }

    /**
     * Appends new time slots to the doctor's availability schedule.
     */
    @Operation(summary = "Add availability slots")
    @PostMapping("/slots")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> addSlots(HttpServletRequest request, @RequestBody List<SlotDTO> slots) {
        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        doctorService.addAvailabilitySlots(email, slots);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Removes an specific availability slot.
     */
    @Operation(summary = "Remove an availability slot")
    @DeleteMapping("/slots")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<Void> removeSlot(HttpServletRequest request, @RequestBody SlotDTO slotDTO) {
        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        doctorService.removeAvailabilitySlot(email, slotDTO);
        return ResponseEntity.noContent().build();
    }

    /**
     * Clinical tool for doctors to review patient history and symptom reports.
     */
    @GetMapping("/patient/{patientId}/symptom-reports")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Get recent patient symptom reports (Paginated)")
    public ResponseEntity<Page<SymptomReportBriefDTO>> getPatientSymptomReports(
            @PathVariable String patientId,
            @ParameterObject Pageable pageable) {

        Page<SymptomReportBriefDTO> reports = doctorService.getPatientSymptomReports(patientId, pageable);
        return ResponseEntity.ok(reports);
    }
}