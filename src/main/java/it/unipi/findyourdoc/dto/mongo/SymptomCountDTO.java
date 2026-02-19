package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for localized health surveillance.
 * Aggregates symptom occurrences within a specific geographic or demographic scope.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Represents the frequency of a specific symptom in a geographic area.")
public class SymptomCountDTO {

    /** * Name of the clinical sign or symptom reported by patients. */
    @Schema(description = "The name of the symptom", example = "Cough")
    private String symptom;

    /** * Total number of independent reports for this symptom in the filtered range. */
    @Schema(description = "How many times this symptom was reported", example = "154")
    private Long count;
}