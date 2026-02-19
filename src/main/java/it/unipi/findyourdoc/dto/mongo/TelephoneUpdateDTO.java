package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Specialized DTO for updating user contact information.
 * Used to isolate telephone modifications from broader profile updates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Data Transfer Object representing the telephone number")
public class TelephoneUpdateDTO {

    /** * Updated contact number for the user or professional. */
    @Schema(description = "Telephone number", example = "369852147")
    protected String telephone;
}