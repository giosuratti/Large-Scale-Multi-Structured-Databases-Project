package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base abstract DTO for user identity and contact details.
 * Ensures uniform handling of identifiers (email) and communication channels (telephone)
 * across all system personas.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        description =
                "Abstract Data Transfer Object representing the common attributes of any user in the system.")
public abstract class UserDTO {

    /** * Primary user identifier and login credential.
     * Validated for standard RFC 5322 compliance.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Primary contact email (used for login)", example = "name.surname@example.com")
    protected String email;

    /** * Secondary contact channel for notifications and platform communication. */
    @Schema(description = "User contact telephone number", example = "369852147")
    protected String telephone;

}