package it.unipi.findyourdoc.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object (DTO) for handling user login requests.
 * Captures the credentials (email and password) required for authentication.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request object for authentication containing email and password.")
public class LoginRequestDTO {

    /** The email of the user attempting to log in. */
    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Schema(description = "The unique email of the user.", example = "user.surname@example.com")
    private String email;

    /** The password associated with the user's account. */
    @NotBlank(message = "Password is required")
    @Schema(description = "The password of the user.", example = "SecurePass123!")
    private String password;
}
