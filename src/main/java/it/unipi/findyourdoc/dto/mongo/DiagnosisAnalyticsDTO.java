package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for tracking diagnosis prevalence across demographics.
 * Used by the analytics engine to visualize health trends based on age and gender filters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Frequency of a potential diagnosis within a specific age group and gender.")
public class DiagnosisAnalyticsDTO {

    /** * Identifying name of the pathology or medical condition. */
    @Schema(description = "The name of the potential diagnosis", example = "Seasonal Influenza")
    private String diagnosis;

    /** * Total occurrences found in the analyzed dataset. */
    @Schema(description = "Number of reports suggesting this diagnosis", example = "89")
    private Long frequency;
}