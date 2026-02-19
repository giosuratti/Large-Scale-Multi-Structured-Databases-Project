package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * DTO for handling forced password changes.
 * Utilized primarily by administrators to update user credentials securely.
 */
@Data
@Schema(description = "Request to force password change by an Administrator")
public class PasswordChangeDTO {

    /** * Plaintext new password to be hashed before persisting to the database. */
    @NotBlank(message = "Password cannot be empty")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Schema(description = "The new secure password for the account", example = "NewSecurePass2026!")
    private String newPassword;
}