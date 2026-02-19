package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Abstract Data Transfer Object for reading user data.
 * It extends the base {@link UserDTO} by adding the database unique identifier
 * required for client-side reference and updates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        description =
                "Abstract Data Transfer Object representing the common attributes of any user in the system during read operations.")
public abstract class UserReadDTO extends UserDTO {

    /** * The unique MongoDB identifier.
     * Represented as a String to ensure compatibility between MongoDB's ObjectId and JSON.
     */
    @Schema(description = "The unique MongoDB identifier.", example = "507f1f77bcf86cd799439011")
    protected String id;
}