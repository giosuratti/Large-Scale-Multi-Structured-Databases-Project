package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DTO representing an Admin, containing base user information.")
public class AdminReadDTO extends UserDTO {
    // Non aggiungiamo nulla perché Admin nel Model non ha campi extra rispetto a User
}