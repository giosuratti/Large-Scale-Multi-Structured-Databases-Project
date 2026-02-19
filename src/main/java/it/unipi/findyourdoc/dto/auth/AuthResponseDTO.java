package it.unipi.findyourdoc.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for the authentication response.
 * Contains the JWT and user authorization context.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response object containing the JWT authentication token and user context.")
public class AuthResponseDTO {

    /** * The JSON Web Token (JWT) issued for the session.
     */
    @Schema(
            description = "JWT access token for authorizing subsequent requests.",
            example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    /** * Token category, strictly "Bearer".
     */
    @Schema(description = "The type of the authentication token.", example = "Bearer")
    private String tokenType = "Bearer";

    /** * Authenticated user identifier.
     */
    @Schema(description = "The email of the authenticated user.", example = "doctor@findyourdoc.it")
    private String email;

    /** * Assigned security role for RBAC.
     */
    @Schema(description = "The role assigned to the user (ADMIN, DOCTOR, PATIENT).", example = "DOCTOR")
    private String role;

    /**
     * Standard constructor for manual token issuance.
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