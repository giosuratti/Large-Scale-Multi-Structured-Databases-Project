package it.unipi.findyourdoc.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import it.unipi.findyourdoc.repository.mongo.AdminRepository;
import it.unipi.findyourdoc.repository.mongo.PatientRepository;
import it.unipi.findyourdoc.repository.mongo.DoctorRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Provider class responsible for managing JSON Web Tokens (JWT).
 * Handles the generation, validation, and extraction of authentication details from JWTs.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final AdminRepository adminRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    @Value("${jwt.secret-key}")
    private String secretKey;

    @Value("${jwt.validity-in-milliseconds}")
    private long validityInMilliseconds;

    private Algorithm algorithm;

    /** * Initializes the cryptographic algorithm used for signing the JWTs after dependency injection. */
    @PostConstruct
    protected void init() {
        algorithm = Algorithm.HMAC256(secretKey);
    }

    /**
     * Generates a new JWT for the authenticated user.
     *
     * @param email The email of the user (used as the subject).
     * @param role  The role of the user (e.g., ADMIN, DOCTOR, PATIENT).
     * @return The signed JWT string.
     */
    public String createToken(String email, String role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return JWT.create()
                // <--- FUNDAMENTAL CORRECTION: Set the Subject (Standard JWT)
                .withSubject(email)
                // Here we save "ADMIN", "DOCTOR", etc.
                .withClaim("role", role)
                // .withClaim("email", email) // Redundant if we use Subject, but can be left
                .withIssuedAt(now)
                .withExpiresAt(validity)
                .sign(algorithm);
    }

    /**
     * Extracts the JWT from the Authorization header of the HTTP request.
     */
    public String resolveToken(HttpServletRequest req) {
        String bearerToken = req.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Validates the cryptographic signature and the expiration of the token.
     * Also verifies that the user associated with the token still exists in the database.
     */
    public boolean validateToken(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token);

            // Now this will work because we used .withSubject() during creation
            String email = jwt.getSubject();
            String role = jwt.getClaim("role").asString();

            if (email == null || role == null) return false;

            // Verify that the user still exists in the DB (Optional but secure)
            return switch (role.toUpperCase()) {
                case "ADMIN" -> adminRepository.existsByEmail(email);
                case "DOCTOR" -> doctorRepository.existsByEmail(email);
                case "PATIENT" -> patientRepository.existsByEmail(email);
                default -> false;
            };

        } catch (JWTVerificationException | NullPointerException e) {
            return false;
        }
    }

    /**
     * Builds a Spring Security Authentication object from the parsed JWT.
     */
    public UsernamePasswordAuthenticationToken getAuthentication(String token) {
        DecodedJWT decodedJWT = JWT.require(algorithm).build().verify(token);

        // Get the email from the Subject (standard)
        String email = decodedJWT.getSubject();
        String role = decodedJWT.getClaim("role").asString();

        // Spring Security requires "ROLE_ADMIN", but the token contains "ADMIN".
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

        return new UsernamePasswordAuthenticationToken(
                email, null, Collections.singletonList(authority));
    }

    /**
     * Utility method to quickly extract the user's email directly from a token.
     */
    public String getEmailFromToken(String token) {
        DecodedJWT decodedJWT = JWT.require(algorithm).build().verify(token);
        return decodedJWT.getSubject(); // Now returns the correct email
    }
}