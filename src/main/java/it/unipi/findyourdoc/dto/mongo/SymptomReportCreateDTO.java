package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;

@Data
@Schema(description = "DTO per creare un nuovo report dei sintomi")
public class SymptomReportCreateDTO {
    @Schema(description = "Il contesto o descrizione dell'insorgenza", example = "Dopo cena ho iniziato a sentire dolore")
    private String context;

    @Schema(description = "Lista dei sintomi avvertiti", example = "['Nausea', 'Mal di stomaco']")
    private ArrayList<String> symptoms;

    @Schema(description = "The date and time when the report was generated", example = "2026-01-22T17:00:00")
    private LocalDateTime createdAt;
}