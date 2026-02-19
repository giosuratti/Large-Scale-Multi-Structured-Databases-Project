package it.unipi.findyourdoc.service;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.model.mongo.SymptomReportBrief;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for managing Medical Doctor entities.
 * Defines business logic for specialized profile updates, availability management, and appointment retrieval.
 */
public interface DoctorService {

    /** * Updates an existing doctor's geographic location. */
    DoctorReadDTO updateDoctorLocation(String email, LocationDTO locationDTO);

    /** * Retrieves a doctor's profile by their unique email address. */
    DoctorReadDTO getDoctorByEmail(String email);

    /** * Retrieves all scheduled appointments for a specific doctor based on their email. */
    List<AppointmentFullDTO> getAppointmentsByEmail(String email);

    /** * Retrieves a paginated list of individual ratings received by the specified doctor. */
    Page<Integer> getRatingsByDoctorEmail(String email, Pageable pageable);

    /** * Adds new available time slots to the doctor's schedule for future patient bookings. */
    void addAvailabilitySlots(String email, List<SlotDTO> slots);

    /** * Removes a specific available time slot from the doctor's schedule. */
    void removeAvailabilitySlot(String email, SlotDTO slotDTO);

    /** * Clears cached data for a specific doctor to ensure fresh data is retrieved on the next query. */
    void invalidateDoctorCache(String id);

    /** * Retrieves a paginated list of symptom reports for a specific patient, allowing the doctor to review medical history. */
    Page<SymptomReportBriefDTO> getPatientSymptomReports(String patientId, Pageable pageable);

    /** * Securely updates the doctor's authentication password. */
    void updateDoctorPassword(String email, PasswordChangeDTO newPassword);

    /** * Updates the doctor's contact telephone number. */
    DoctorReadDTO updateDoctorPhone(String email, TelephoneUpdateDTO phoneDTO);
}