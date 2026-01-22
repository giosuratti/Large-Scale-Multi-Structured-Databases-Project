package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
@Schema(description = "DTO for updating an existing Administrator")
public class AdminUpdateDTO {

    @Email(message = "Invalid email format")
    @Schema(description = "New email address", example = "updated.admin@findyourdoc.it")
    private String email;

    @Schema(description = "New telephone number", example = "3331122334")
    private String telephone;

    @Schema(description = "New password if a change is requested", example = "NewStrongPass77!")
    private String password;
}