package it.unipi.findyourdoc.service;

import it.unipi.findyourdoc.dto.mongo.*;

import java.util.List;

/**
 * Service interface for managing Medical Doctor entities.
 *
 * <p>Defines the business logic for professional registration, specialized profile updates,
 * and retrieval of doctor information based on their unique email identifier.
 */
public interface DoctorService {

    /**
     * Registers a new doctor in the system.
     *
     * @param createDTO The DTO containing the doctor's professional and credential data.
     * @return The created doctor profile details.
     * @throws org.springframework.web.server.ResponseStatusException If the email is already in use.
     */
    DoctorReadDTO registerDoctor(DoctorCreateDTO createDTO);

    /**
     * Updates an existing doctor's professional profile.
     *
     * <p>Allows updating professional details like specialization and location,
     * as well as credentials like email or password.
     *
     * @param email The unique email of the doctor to update.
     * @param updateDTO The DTO containing the fields to update.
     * @return The updated doctor profile details.
     * @throws org.springframework.web.server.ResponseStatusException If the doctor is not found or new email is taken.
     */
    DoctorReadDTO updateDoctor(String email, DoctorUpdateDTO updateDTO);

    /**
     * Retrieves a doctor's profile by their unique email address.
     *
     * @param email The doctor's unique email.
     * @return The found doctor details.
     * @throws org.springframework.web.server.ResponseStatusException If the doctor is not found.
     */
    DoctorReadDTO getDoctorByEmail(String email);

    List<AppointmentDTO> getAppointmentsByEmail(String email);

    List<RatingDTO> getRatingsByDoctorEmail(String email);

    void addAvailabilitySlots(String email, List<SlotDTO> slots);

    void removeAvailabilitySlot(String email, SlotDTO slotDTO);
}