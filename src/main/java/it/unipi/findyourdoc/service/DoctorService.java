package it.unipi.findyourdoc.service;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.model.mongo.SymptomReportBrief;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for managing Medical Doctor entities.
 *
 * <p>Defines the business logic for professional registration, specialized profile updates,
 * and retrieval of doctor information based on their unique email identifier.
 */
public interface DoctorService {




    /**
     * Updates an existing doctor's professional profile.
     *
     * <p>Allows updating professional details like specialization and location,
     * as well as credentials like email or password.
     *
     * @param email The unique email of the doctor to update.
     * @return The updated doctor profile details.
     * @throws org.springframework.web.server.ResponseStatusException If the doctor is not found or new email is taken.
     */
    DoctorReadDTO updateDoctorLocation(String email, LocationDTO locationDTO);

    /**
     * Retrieves a doctor's profile by their unique email address.
     *
     * @param email The doctor's unique email.
     * @return The found doctor details.
     * @throws org.springframework.web.server.ResponseStatusException If the doctor is not found.
     */
    DoctorReadDTO getDoctorByEmail(String email);

    List<AppointmentFullDTO> getAppointmentsByEmail(String email);

    Page<Integer> getRatingsByDoctorEmail(String email, Pageable pageable);

    void addAvailabilitySlots(String email, List<SlotDTO> slots);

    void removeAvailabilitySlot(String email, SlotDTO slotDTO);

    void invalidateDoctorCache(String id);

    Page<SymptomReportBriefDTO> getPatientSymptomReports(String patientId, Pageable pageable);

    void updateDoctorPassword(String email, PasswordChangeDTO newPassword);

    DoctorReadDTO updateDoctorPhone(String email, TelephoneUpdateDTO phoneDTO);
}