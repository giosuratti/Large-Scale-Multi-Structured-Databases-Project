package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;

/**
 * Base DTO for doctor profile data.
 * Provides core professional information and aggregated reputation metrics.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DTO for reading doctor profile and professional information")
public class DoctorReadDTOBase extends UserReadDTO {

    /** * Unique identifier for healthcare providers across the platform. */
    @Schema(description = "Unique National Provider Identifier", example = "1234567890")
    protected String npi;

    @Schema(description = "The doctor's first name", example = "Giulia")
    protected String firstName;

    @Schema(description = "The doctor's last name", example = "Bianchi")
    protected String lastName;

    /** * List of medical fields of expertise for the professional. */
    @Schema(description = "Medical specialization", example = "Cardiology")
    protected ArrayList<String> specializations;

    @Schema(description = "The doctor's gender", example = "Female")
    protected String gender;

    /** * Physical office coordinates and address details. */
    @Schema(description = "The doctor's office location")
    protected LocationDTO location;

    /** * Aggregated score calculated from all patient feedback. */
    @Schema(
            description = "Average of individual scores provided by patients for this doctor.",
            example = "4.5"
    )
    protected Double avgRating;

    /** * Total volume of ratings received to date. */
    @Schema(
            description = "Number of individual scores provided by patients for this doctor.",
            example = "8"
    )
    protected Integer ratingCount;
}