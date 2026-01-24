package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Represents the frequency of a specific symptom in a geographic area.")
public class SymptomCountDTO {

    @Schema(description = "The name of the symptom", example = "Cough")
    private String symptom;

    @Schema(description = "How many times this symptom was reported", example = "154")
    private Long count;
}