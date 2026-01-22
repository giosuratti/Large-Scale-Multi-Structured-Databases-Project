package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO representing a rating given by a patient to a doctor")
public class RatingDTO {

    @NotBlank(message = "Doctor ID is required")
    @Schema(description = "The unique ID of the rated doctor", example = "60d5ecb8b39d1c2b4c8e9f1a")
    private String doctorId;

    @Schema(description = "Full name of the doctor (optional in DTO)", example = "Mario")
    private String doctorName;

    @Schema(description = "Surname of the doctor (optional in DTO)", example = "Rossi")
    private String doctorSurname;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    @Schema(description = "The score given (1 to 5)", example = "5")
    private int rating;
}
