package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for submitting or viewing a patient's evaluation of a doctor.
 * Encapsulates the numeric score and the target professional's identity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO representing a rating given by a patient to a doctor")
public class RatingDTO {

    /** * Target doctor's NPI, used as the primary key for reputation updates. */
    @NotBlank(message = "Doctor NPI is required")
    @Schema(description = "The unique NPI of the rated doctor", example = "1234567890")
    private String doctorNpi;

    @Schema(description = "First name of the doctor", example = "Mario")
    private String doctorFirstName;

    @Schema(description = "Surname of the doctor", example = "Rossi")
    private String doctorLastName;

    /** * Numeric feedback score. Must be between 1 (poor) and 5 (excellent). */
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    @Schema(description = "The score given (1 to 5)", example = "5")
    private Integer rating;
}