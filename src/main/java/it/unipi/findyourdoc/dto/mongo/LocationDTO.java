package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for physical address representation.
 * Standardizes geographic data for patients, doctors, and medical facilities.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO representing the geographic location of a user or doctor")
public class LocationDTO {

    /** * Specific street address and building number. */
    @NotBlank(message = "Address is required")
    @Schema(description = "Street name and number", example = "Largo Lucio Lazzarino, 1")
    private String address;

    /** * City or municipality. */
    @NotBlank(message = "City is required")
    @Schema(description = "City name", example = "Pisa")
    private String city;

    /** * Regional administrative division or province code. */
    @NotBlank(message = "State/Province is required")
    @Schema(description = "State or Province abbreviation", example = "PI")
    private String state;

    /** * Postal/ZIP code for area-based lookups. */
    @NotBlank(message = "Zip code is required")
    @Schema(description = "Postal code", example = "56122")
    private String zipCode;
}