package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DTO for reading doctor profile and professional information")
public class DoctorReadDTOBase extends UserReadDTO {

    @Schema(description = "Unique National Provider Identifier", example = "1234567890")
    protected String npi;

    @Schema(description = "The doctor's first name", example = "Giulia")
    protected String firstName;

    @Schema(description = "The doctor's last name", example = "Bianchi")
    protected String lastName;

    @Schema(description = "Medical specialization", example = "Cardiology")
    protected ArrayList<String> specializations;

    @Schema(description = "The doctor's gender", example = "Female")
    protected String gender;

    @Schema(description = "The doctor's office location")
    protected LocationDTO location;

    @Schema(
            description = "Average of individual scores provided by patients for this doctor. Each value is an integer between 1 and 5.",
            example = "4.5"
    )
    protected Double avgRating;

    @Schema(
            description = "Number of individual scores provided by patients for this doctor. Each value is an integer between 1 and 5.",
            example = "8"
    )
    protected Integer ratingCount;
}