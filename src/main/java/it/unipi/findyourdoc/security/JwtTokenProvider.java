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
 * Component responsible for JWT (JSON Web Token) lifecycle management.
 * * Adapted for FindYourDoc: handles Admin, Doctor, and Patient validation.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    // Injecting specific repositories for each user type
    private final AdminRepository adminRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;

    @Value("${jwt.secret-key}")
    private String secretKey;

    @Value("${jwt.validity-in-milliseconds}")
    private long validityInMilliseconds;

    private Algorithm algorithm;

    @PostConstruct
    protected void init() {
        algorithm = Algorithm.HMAC256(secretKey);
    }

    public String createToken(String email, String role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return JWT.create()
                .withClaim("role", role)
                .withClaim("email", email)
                .withIssuedAt(now)
                .withExpiresAt(validity)
                .sign(algorithm);
    }

    public String resolveToken(HttpServletRequest req) {
        String bearerToken = req.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Validates the token and checks the existence of the specific user type
     * in its respective MongoDB collection.
     */
    public boolean validateToken(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token);

            String username = jwt.getSubject();
            String role = jwt.getClaim("role").asString();

            // Check existence based on the specific role
            if (role == null) return false;

            return switch (role.toUpperCase()) {
                case "ADMIN" -> adminRepository.existsByEmail(username);
                case "DOCTOR" -> doctorRepository.existsByEmail(username);
                case "PATIENT" -> patientRepository.existsByEmail(username);
                default -> false;
            };

        } catch (JWTVerificationException | NullPointerException e) {
            return false;
        }
    }

    public UsernamePasswordAuthenticationToken getAuthentication(String token) {
        DecodedJWT decodedJWT = JWT.require(algorithm).build().verify(token);

        String email = decodedJWT.getClaim("email").asString();
        String role = decodedJWT.getClaim("role").asString();

        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

        return new UsernamePasswordAuthenticationToken(
                email, null, Collections.singletonList(authority));
    }

    public String getPatientEmailFromToken(String token) {
        DecodedJWT decodedJWT = JWT.require(algorithm).build().verify(token);
        return decodedJWT.getClaim("email").asString();
    }

    public String getEmailFromToken(String token) {
        DecodedJWT decodedJWT = JWT.require(algorithm).build().verify(token);
        return decodedJWT.getSubject();
    }
}
