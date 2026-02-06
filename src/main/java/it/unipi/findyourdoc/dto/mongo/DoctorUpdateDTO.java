package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "DTO for updating an existing doctor's profile. Fields can be null if no change is needed.")
public class DoctorUpdateDTO extends UserUpdateDTO {

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