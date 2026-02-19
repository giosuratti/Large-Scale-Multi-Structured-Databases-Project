package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * DTO for updating Administrator profile details.
 * Extends UserUpdateDTO to maintain consistent update logic across the platform.
 */
@Data
@Schema(description = "DTO for updating an existing Administrator")
public class AdminUpdateDTO extends UserUpdateDTO {

    // Inherits editable fields (e.g., name, email) from UserUpdateDTO.
    // Provides a dedicated type for administrative update operations.
}