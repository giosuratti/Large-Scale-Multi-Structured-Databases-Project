package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "DTO for updating an existing Administrator")
public class AdminUpdateDTO extends UserUpdateDTO{

}