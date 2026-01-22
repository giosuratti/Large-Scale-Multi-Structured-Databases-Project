package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO representing a summary of a symptom analysis report")
public class SymptomReportBriefDTO {

    @Schema(description = "Additional context or notes provided by the user", example = "Sintomi avvertiti dopo attività fisica")
    private String context;

    @Schema(description = "List of symptoms reported by the patient", example = "['Tosse', 'Fiato corto']")
    private List<String> symptoms;

    @Schema(description = "List of possible diseases identified by the system", example = "['Asma', 'Bronchite']")
    private List<String> possibleDiagnosies;

    @Schema(description = "The date and time when the report was generated", example = "2026-01-22T17:00:00")
    private LocalDateTime createdAt;
}
