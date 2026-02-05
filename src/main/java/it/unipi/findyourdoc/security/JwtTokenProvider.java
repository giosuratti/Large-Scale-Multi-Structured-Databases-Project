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

    @PostConstruct
    protected void init() {
        algorithm = Algorithm.HMAC256(secretKey);
    }

    public String createToken(String email, String role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return JWT.create()
                .withSubject(email) // <--- CORREZIONE FONDAMENTALE: Imposta il Subject (Standard JWT)
                .withClaim("role", role) // Qui salviamo "ADMIN", "DOCTOR", ecc.
                // .withClaim("email", email) // Ridondante se usiamo Subject, ma puoi lasciarlo
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

    public boolean validateToken(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token);

            // Ora questo funzionerà perché abbiamo usato .withSubject() nella creazione
            String email = jwt.getSubject();
            String role = jwt.getClaim("role").asString();

            if (email == null || role == null) return false;

            // Verifica che l'utente esista ancora nel DB (Opzionale ma sicuro)
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

    public UsernamePasswordAuthenticationToken getAuthentication(String token) {
        DecodedJWT decodedJWT = JWT.require(algorithm).build().verify(token);

        // Prendo la mail dal Subject (standard)
        String email = decodedJWT.getSubject();
        String role = decodedJWT.getClaim("role").asString();

        // <--- QUI È DOVE RISOLVI IL PROBLEMA DEL "ROLE_"
        // Spring Security vuole "ROLE_ADMIN", nel token c'è scritto "ADMIN".
        // Lo aggiungiamo manualmente qui. PERFETTO.
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

        return new UsernamePasswordAuthenticationToken(
                email, null, Collections.singletonList(authority));
    }

    public String getEmailFromToken(String token) {
        DecodedJWT decodedJWT = JWT.require(algorithm).build().verify(token);
        return decodedJWT.getSubject(); // Ora ritorna la mail corretta
    }
}