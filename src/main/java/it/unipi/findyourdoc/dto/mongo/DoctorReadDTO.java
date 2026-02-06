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
public class DoctorReadDTO extends UserReadDTO {

    @Schema(description = "Unique National Provider Identifier", example = "1234567890")
    private String npi;

    @Schema(description = "The doctor's first name", example = "Giulia")
    private String firstName;

    @Schema(description = "The doctor's last name", example = "Bianchi")
    private String lastName;

    @Schema(description = "Medical specialization", example = "Cardiology")
    private ArrayList<String> specializations;

    @Schema(description = "The doctor's gender", example = "Female")
    private String gender;

    @Schema(description = "The doctor's office location")
    private LocationDTO location;

    @Schema(
            description = "List of individual scores provided by patients for this doctor. Each value is an integer between 1 and 5.",
            example = "[5, 4, 5, 3, 4]",
            type = "array"
    )
    private ArrayList<Integer> ratings;
}