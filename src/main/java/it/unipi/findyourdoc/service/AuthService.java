package it.unipi.findyourdoc.service;

import it.unipi.findyourdoc.dto.auth.AuthResponseDTO;
import it.unipi.findyourdoc.dto.auth.LoginRequestDTO;

/**
 * Service interface for handling authentication operations in FindYourDoc.
 * Defines the contract for authenticating different types of users (Admins, Doctors, and Patients)
 * and issuing JWT tokens upon successful validation of credentials.
 */
public interface AuthService {

    /**
     * Authenticates an Administrator.
     *
     * @param loginRequest The DTO containing the admin's email and password.
     * @return An {@link AuthResponseDTO} containing the generated JWT token and user context.
     * @throws org.springframework.security.authentication.BadCredentialsException If authentication fails.
     */
    AuthResponseDTO loginAdmin(LoginRequestDTO loginRequest);

    /**
     * Authenticates a Medical Professional (Doctor).
     *
     * @param loginRequest The DTO containing the doctor's email and password.
     * @return An {@link AuthResponseDTO} containing the generated JWT token and user context.
     * @throws org.springframework.security.authentication.BadCredentialsException If authentication fails.
     */
    AuthResponseDTO loginDoctor(LoginRequestDTO loginRequest);

    /**
     * Authenticates a Registered Patient.
     *
     * @param loginRequest The DTO containing the patient's email and password.
     * @return An {@link AuthResponseDTO} containing the generated JWT token and user context.
     * @throws org.springframework.security.authentication.BadCredentialsException If authentication fails.
     */
    AuthResponseDTO loginPatient(LoginRequestDTO loginRequest);
}