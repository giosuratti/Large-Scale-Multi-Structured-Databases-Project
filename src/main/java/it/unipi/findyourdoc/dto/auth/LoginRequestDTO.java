package it.unipi.findyourdoc.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for capturing user credentials.
 * Used across Admin, Doctor, and Patient login flows.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request object for authentication containing email and password.")
public class LoginRequestDTO {

    /** * User's primary identifier.
     * Must follow standard email format.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Schema(description = "The unique email of the user.", example = "user.surname@example.com")
    private String email;

    /** * Plaintext password for verification.
     * Hashing is performed at the Service layer.
     */
    @NotBlank(message = "Password is required")
    @Schema(description = "The password of the user.", example = "SecurePass123!")
    private String password;
}