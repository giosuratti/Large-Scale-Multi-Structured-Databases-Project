package it.unipi.findyourdoc.dto.mongo;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Abstract Data Transfer Object representing the common attributes of any user in the system.
 *
 * <p>Serves as a base class for specific user types (e.g., RegisteredUser, Admin) to ensure
 * consistent field naming for ID, username, and email in API responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        description =
                "Abstract Data Transfer Object representing the common attributes of any user in the system during create operations.")
public abstract class UserCreateDTO extends UserDTO {
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Schema(description = "The raw password (plaintext).", example = "AdminSecret123!")
    protected String password;
}
