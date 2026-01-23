package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.Data;

import java.util.ArrayList;

@Data
@Schema(description = "DTO for updating an existing doctor's profile. Fields can be null if no change is needed.")
public class DoctorUpdateDTO {

    @Email(message = "Invalid email format")
    @Schema(description = "New contact email address", example = "dr.bianchi.updated@example.com")
    private String email;

    @Schema(description = "New telephone number", example = "3339876543")
    private String telephone;

    @Schema(description = "New password if a change is requested", example = "NewSecurePass2026!")
    private String password;

    @Schema(description = "Updated first name", example = "Giulia Maria")
    private String firstName;

    @Schema(description = "Updated last name", example = "Bianchi Rossi")
    private String lastName;

    @Schema(description = "Updated medical specialization", example = "Neurology")
    private ArrayList<String> specializations;

    @Schema(description = "Updated gender", example = "Female")
    private String gender;

    @Schema(description = "Updated office location details")
    private LocationDTO location;
}