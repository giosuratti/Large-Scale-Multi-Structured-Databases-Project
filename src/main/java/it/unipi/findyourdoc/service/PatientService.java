package it.unipi.findyourdoc.service;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.dto.neo4j.SpecialistDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for managing Registered User entities.
 *
 * <p>Defines the business logic for user registration, profile updates (including sensitive data
 * like passwords), information retrieval, and search capabilities.
 */
public interface PatientService {

    /**
     * Registers a new user in the system.
     *
     * @param createDTO The DTO containing the initial user data.
     * @return The created user details.
     * @throws IllegalArgumentException If the email is already in use.
     */
    PatientReadDTO registerPatient(PatientCreateDTO createDTO);

    /**
     * Updates an existing user's profile.
     *
     * <p>Allows updating the email, username, password, and full name. Performs necessary validation
     * to ensure uniqueness of email and username.
     *
     * @param email The unique identifier of the user to update.
     * @param updateDTO The DTO containing the fields to update.
     * @return The updated user details.
     * @throws IllegalArgumentException If the new email or username is already taken.
     */
    PatientReadDTO updatePatient(String email, PatientUpdateDTO updateDTO);

    PatientReadDTO getPatientByEmail(String email);

    AppointmentPatientDTO bookAppointmentByEmail(String id, AppointmentBookDTO appointmentBookDTO);

    void cancelAppointment(String id);

    Page<AppointmentPatientDTO> getAppointmentsByEmail(String email, Pageable pageable);

    Page<SymptomReportBriefDTO> getSymptomReportsByEmail(String email, Pageable pageable);

    SymptomReportBriefDTO createSymptomReportByEmail(String email, SymptomReportCreateDTO createDTO);

    RatingDTO addRatingByEmail (String email, RatingDTO ratingDTO);

    Page<RatingDTO> getAllRatingsByEmail(String email, Pageable pageable);

    List<SpecialistDTO> findSpecialistsByDiagnosisAndCity(String city, String diagnosis);

    DoctorReadSlotsDTO getDoctorByNpi(String id);


}