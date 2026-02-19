package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * DTO for Administrator account registration.
 * Inherits core identity and credential fields from UserCreateDTO.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "DTO for registering a new Administrator")
public class AdminCreateDTO extends UserCreateDTO {

    // This class currently acts as a specialized marker for Admin creation.
    // It utilizes the inherited fields (email, password, name) from the parent class.
}