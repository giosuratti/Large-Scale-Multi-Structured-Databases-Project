package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.auth.AuthResponseDTO;
import it.unipi.findyourdoc.dto.auth.LoginRequestDTO;
import it.unipi.findyourdoc.model.mongo.Admin;
import it.unipi.findyourdoc.model.mongo.Doctor;
import it.unipi.findyourdoc.model.mongo.Patient;
import it.unipi.findyourdoc.repository.mongo.AdminRepository;
import it.unipi.findyourdoc.repository.mongo.DoctorRepository;
import it.unipi.findyourdoc.repository.mongo.PatientRepository;
import it.unipi.findyourdoc.security.JwtTokenProvider;
import it.unipi.findyourdoc.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Implementation of the AuthService for FindYourDoc.
 * Handles credential verification and JWT generation for Admins, Doctors, and Patients.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImplementation implements AuthService {

    private final AdminRepository adminRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Override
    public AuthResponseDTO loginAdmin(LoginRequestDTO loginRequest) {
        String email = loginRequest.getEmail();
        log.info("Attempting ADMIN login for: {}", email);

        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Admin not found with email: " + email));

        verifyPassword(loginRequest.getPassword(), admin.getPassword(), email);

        String token = jwtTokenProvider.createToken(admin.getEmail(), "ADMIN");
        return new AuthResponseDTO(token, admin.getEmail(), "ADMIN");
    }

    @Override
    public AuthResponseDTO loginDoctor(LoginRequestDTO loginRequest) {
        String email = loginRequest.getEmail();
        log.info("Attempting DOCTOR login for: {}", email);

        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Doctor not found with email: " + email));

        verifyPassword(loginRequest.getPassword(), doctor.getPassword(), email);

        String token = jwtTokenProvider.createToken(doctor.getEmail(), "DOCTOR");
        return new AuthResponseDTO(token, doctor.getEmail(), "DOCTOR");
    }

    @Override
    public AuthResponseDTO loginPatient(LoginRequestDTO loginRequest) {
        String email = loginRequest.getEmail();
        log.info("Attempting PATIENT login for: {}", email);

        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Patient not found with email: " + email));

        verifyPassword(loginRequest.getPassword(), patient.getPassword(), email);

        String token = jwtTokenProvider.createToken(patient.getEmail(), "PATIENT");
        return new AuthResponseDTO(token, patient.getEmail(), "PATIENT");
    }

    /**
     * Helper method to verify passwords and log failures.
     */
    private void verifyPassword(String rawPassword, String encodedPassword, String email) {
        if (!passwordEncoder.matches(rawPassword, encodedPassword)) {
            log.warn("Invalid password attempt for user: {}", email);
            throw new BadCredentialsException("Invalid credentials");
        }
    }
}