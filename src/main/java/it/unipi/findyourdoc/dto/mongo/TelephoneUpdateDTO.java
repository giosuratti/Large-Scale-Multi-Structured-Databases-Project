package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        description =
                "Data Transfer Object representing the telephone number")
public class TelephoneUpdateDTO {
    @Schema(description = "Telephone number", example = "369852147")
    protected String telephone;
}
