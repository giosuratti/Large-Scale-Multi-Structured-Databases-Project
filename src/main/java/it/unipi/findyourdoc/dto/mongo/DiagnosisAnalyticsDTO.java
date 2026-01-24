package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Frequency of a potential diagnosis within a specific age group and gender.")
public class DiagnosisAnalyticsDTO {

    @Schema(description = "The name of the potential diagnosis", example = "Seasonal Influenza")
    private String diagnosis;

    @Schema(description = "Number of reports suggesting this diagnosis", example = "89")
    private Long frequency;
}