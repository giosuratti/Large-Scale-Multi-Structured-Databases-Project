package it.unipi.findyourdoc.service;

import it.unipi.findyourdoc.dto.mongo.PatientCreateDTO;
import it.unipi.findyourdoc.dto.mongo.PatientReadDTO;
import it.unipi.findyourdoc.dto.mongo.PatientUpdateDTO;

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
     * @param id The unique identifier of the user to update.
     * @param updateDTO The DTO containing the fields to update.
     * @return The updated user details.
     * @throws IllegalArgumentException If the new email or username is already taken.
     */
    PatientReadDTO updatePatient(String id, PatientUpdateDTO updateDTO);

    /**
     * Retrieves a user by their unique ID.
     *
     * @param id The user's ID.
     * @return The found user details.
     */
    PatientReadDTO getUserByEmail(String email);

}