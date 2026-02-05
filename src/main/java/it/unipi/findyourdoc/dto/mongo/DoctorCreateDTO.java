package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DTO for registering a new medical professional, including credentials and professional info")
public class DoctorCreateDTO extends UserDTO {

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Schema(description = "The raw password (plaintext) to be encrypted", example = "DoctorStrongPass123!")
    private String password;

    @NotBlank(message = "First name is required")
    @Schema(description = "The doctor's first name", example = "Giulia")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Schema(description = "The doctor's last name", example = "Bianchi")
    private String lastName;

    // CORREZIONE 1: Usa @NotEmpty invece di @NotBlank per le liste
    @NotEmpty(message = "Specialization is required")
    // CORREZIONE 2: Scrivi l'example come un array JSON scappato "[\"Valore\"]"
    @Schema(description = "The medical area of expertise", example = "[\"Cardiology\", \"Dermatology\"]")
    private ArrayList<String> specializations;

    @NotBlank(message = "Gender is required")
    @Schema(description = "The doctor's gender", example = "Female")
    private String gender;

    @NotNull(message = "Office location is required")
    @Schema(description = "The physical location of the doctor's office")
    private LocationDTO location;
}