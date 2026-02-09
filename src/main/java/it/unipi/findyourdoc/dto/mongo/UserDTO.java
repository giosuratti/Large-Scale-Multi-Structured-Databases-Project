package it.unipi.findyourdoc.dto.mongo;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
                "Abstract Data Transfer Object representing the common attributes of any user in the system.")
public abstract class UserDTO {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Admin contact email (used for login)", example = "name.surname@example.com")
    protected String email;
    @Schema(description = "Admin contact telephone number", example = "369852147")
    protected String telephone;

}
