package it.unipi.findyourdoc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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




    @Operation(summary = "Update only the location", description = "Updates the doctor's address. Does NOT trigger sync with appointments or search engine.")
    @PutMapping("/me/location")
    @PreAuthorize("hasAnyRole('DOCTOR')") // Anche l'admin può farlo se serve
    public ResponseEntity<DoctorReadDTO> updateLocation(
            HttpServletRequest request,
            @RequestBody @Valid LocationDTO locationDTO) { // <-- Prende SOLO LocationDTO

        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        return ResponseEntity.ok(doctorService.updateDoctorLocation(email, locationDTO));
    }

    // --- UPDATE PASSWORD ---
    @Operation(summary = "Update password", description = "Updates login password. No sync needed.")
    @PutMapping("/me/password")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<Void> updatePassword(HttpServletRequest request,
                                               @RequestBody PasswordChangeDTO newPassword) {
        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        doctorService.updateDoctorPassword(email, newPassword);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Update phone number", description = "Updates doctor's phone. Admin sync required for search engine update.")
    @PutMapping("/me/phone")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<DoctorReadDTO> updatePhone(HttpServletRequest request,
                                                     @RequestBody TelephoneUpdateDTO phoneDTO) {
        String email = jwtTokenProvider.getEmailFromToken(jwtTokenProvider.resolveToken(request));
        return ResponseEntity.ok(doctorService.updateDoctorPhone(email, phoneDTO));
    }

    @Operation(
            summary = "Get Current Doctor Info",
            description = "Retrieves the full profile of the currently logged-in doctor using the JWT token.")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DoctorReadDTO> getCurrentDoctorInfo(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token); // Il subject del token è la mail

        DoctorReadDTO doctorProfile = doctorService.getDoctorByEmail(email);

        return ResponseEntity.ok(doctorProfile);
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
    public ResponseEntity<List<AppointmentFullDTO>> getMyAgenda(HttpServletRequest request) {
        // Estraiamo l'email dal token JWT
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        // Chiamiamo il service per recuperare gli appuntamenti
        return ResponseEntity.ok(doctorService.getAppointmentsByEmail(email));
    }


    @Operation(summary = "Get all numeric ratings for a specific doctor",
            description = "Public endpoint to view all integer ratings page by page.")
    @GetMapping("/ratings")
    public ResponseEntity<Page<Integer>> getDoctorRatings(
            HttpServletRequest request,
            @ParameterObject Pageable pageable) { // <-- Aggiungi Pageable

        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        // Restituisce una Pagina di Interi
        return ResponseEntity.ok(doctorService.getRatingsByDoctorEmail(email, pageable));
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

    @GetMapping("/patient/{patientId}/symptom-reports")
    @PreAuthorize("hasRole('DOCTOR')")
    @Operation(summary = "Ottiene i report dei sintomi recenti di un paziente (Paginato)")
    public ResponseEntity<Page<SymptomReportBriefDTO>> getPatientSymptomReports(
            @PathVariable String patientId,
            @ParameterObject Pageable pageable) { // <-- Aggiunto Pageable

        Page<SymptomReportBriefDTO> reports = doctorService.getPatientSymptomReports(patientId, pageable);
        return ResponseEntity.ok(reports);
    }

}