package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * DTO for retrieving Administrator profile data.
 * Acts as a specialized projection of UserReadDTO for administrative contexts.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DTO representing an Admin, containing base user information.")
public class AdminReadDTO extends UserReadDTO {

    // No additional fields required as the Admin model strictly follows the User schema.
    // This class serves to distinguish Admin entities within the application's type system.
}