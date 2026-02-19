package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base abstract DTO for account creation.
 * Standardizes the capture of security credentials across all user roles.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        description =
                "Abstract Data Transfer Object representing the common attributes of any user in the system during create operations.")
public abstract class UserCreateDTO extends UserDTO {

    /** * Plaintext password to be processed by the password encoder.
     * Enforcement of complexity occurs at this layer.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Schema(description = "The raw password (plaintext).", example = "AdminSecret123!")
    protected String password;
}