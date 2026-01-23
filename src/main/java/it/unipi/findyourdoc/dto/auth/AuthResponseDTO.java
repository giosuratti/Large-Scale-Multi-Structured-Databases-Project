package it.unipi.findyourdoc.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for the authentication response.
 * Returns the JWT token along with user context (email and role).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response object containing the JWT authentication token and user context.")
public class AuthResponseDTO {

    /** The JSON Web Token (JWT) issued for the authenticated session. */
    @Schema(
            description = "The JWT access token used for authorizing subsequent requests.",
            example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    /** The type of the token, typically "Bearer". */
    @Schema(description = "The type of the authentication token.", example = "Bearer")
    private String tokenType = "Bearer";

    /** The user's email address. */
    @Schema(description = "The email of the authenticated user.", example = "doctor@findyourdoc.it")
    private String email;

    /** The assigned role (ADMIN, DOCTOR, or PATIENT). */
    @Schema(description = "The role assigned to the user.", example = "DOCTOR")
    private String role;

    /**
     * Minimal constructor for basic token responses.
     *
     * @param accessToken The JWT access token.
     * @param email The user email.
     * @param role The user role.
     */
    public AuthResponseDTO(String accessToken, String email, String role) {
        this.accessToken = accessToken;
        this.email = email;
        this.role = role;
        this.tokenType = "Bearer";
    }
}