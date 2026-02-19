package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user profile and credential updates.
 * Provides a secure structure for modifying contact information and synchronizing password changes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        description =
                "Abstract Data Transfer Object representing the common attributes of any user in the system during update operations.")
public class UserUpdateDTO extends UserDTO {

    /** * New plaintext password to be hashed before updating the security context. */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Schema(description = "The raw password (plaintext).", example = "AdminSecret123!")
    protected String password;
}