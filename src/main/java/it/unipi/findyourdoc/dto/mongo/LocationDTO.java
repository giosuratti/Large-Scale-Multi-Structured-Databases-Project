package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO representing the geographic location of a user or doctor")
public class LocationDTO {

    @NotBlank(message = "Address is required")
    @Schema(description = "Street name and number", example = "Largo Lucio Lazzarino, 1")
    private String address;

    @NotBlank(message = "City is required")
    @Schema(description = "City name", example = "Pisa")
    private String city;

    @NotBlank(message = "State/Province is required")
    @Schema(description = "State or Province abbreviation", example = "PI")
    private String state;

    @NotBlank(message = "Zip code is required")
    @Schema(description = "Postal code", example = "56122")
    private String zipCode;
}
