package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;

/**
 * Data Transfer Object for creating a new symptom report.
 * Captures the textual context of the issue and the specific symptoms experienced by the patient.
 */
@Data
@Schema(description = "DTO for creating a new symptom report")
public class SymptomReportCreateDTO {

    /** * Context or textual description detailing how and when the symptoms started. */
    @Schema(description = "The context or description of the symptom onset", example = "I started feeling pain after dinner")
    private String context;

    /** * A list of specific symptom names identified or experienced by the patient. */
    @Schema(description = "List of symptoms experienced", example = "[\"Nausea\", \"Stomach ache\"]")
    private ArrayList<String> symptoms;
}