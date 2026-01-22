package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DTO for registering a new patient, including credentials and personal details")
public class PatientCreateDTO extends UserDTO {

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Schema(description = "The raw password provided by the user (plaintext).", example = "SecretPass123!")
    private String password;

    @NotBlank(message = "First name is required")
    @Schema(description = "The user's first name.", example = "Mario")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Schema(description = "The user's last name.", example = "Rossi")
    private String lastName;

    @Min(value = 0, message = "Age cannot be negative")
    @Max(value = 120, message = "Please enter a valid age")
    @Schema(description = "The user's age.", example = "25")
    private Integer age;

    @NotBlank(message = "Gender is required")
    @Schema(description = "The user's gender.", example = "Male")
    private String gender;

    @NotNull(message = "Location is required")
    @Schema(description = "The patient's initial geographic location")
    private LocationDTO location;
}