package it.unipi.findyourdoc.dto.neo4j;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a specialist found based on diagnosis and location.
 * Provides detailed information about a doctor identified through graph database matching.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Object containing detailed information about a specialist found near the user.")
public class SpecialistDTO {

    /** * National Provider Identifier used as the unique key for the medical professional. */
    @Schema(description = "Unique NPI of the doctor in the database", example = "65af12345")
    private String npi;

    @Schema(description = "The doctor's first name", example = "Giulia")
    private String firstName;

    @Schema(description = "The doctor's last name", example = "Bianchi")
    private String lastName;

    /** * Calculated average of all ratings assigned to this doctor by patients. */
    @Schema(description = "Average rating received from patients (1 to 5)", example = "4.8")
    private Double avgRating;

    /** * Total number of ratings received, used to weight the average rating. */
    @Schema(description = "Total number of ratings received from patients", example = "25")
    private Integer ratingCount;

    @Schema(description = "The doctor's city of practice")
    private String city;

    @Schema(description = "The doctor's contact telephone number")
    private String phone;
}