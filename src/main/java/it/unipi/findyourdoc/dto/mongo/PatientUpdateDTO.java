package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO for updating an existing patient's profile")
@EqualsAndHashCode(callSuper=false)
public class PatientUpdateDTO extends UserUpdateDTO {


    @Schema(description = "The updated first name.", example = "Mario")
    private String firstName;

    @Schema(description = "The updated last name.", example = "Rossi")
    private String lastName;

    @Min(value = 0, message = "Age cannot be negative")
    @Max(value = 120, message = "Please enter a valid age")
    @Schema(description = "The updated age.", example = "26")
    private Integer age; // Usiamo Integer (oggetto) invece di int (primitivo) per permettere il valore null

    @Schema(description = "The updated gender.", example = "Non-binary")
    private String gender;


    @Schema(description = "The updated geographic location")
    private LocationDTO location;
}