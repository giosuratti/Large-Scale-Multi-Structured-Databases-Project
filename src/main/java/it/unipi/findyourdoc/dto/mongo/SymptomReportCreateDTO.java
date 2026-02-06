package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;

@Data
@Schema(description = "DTO for creating a new symptom report")
public class SymptomReportCreateDTO {

    @Schema(description = "The context or description of the symptom onset", example = "I started feeling pain after dinner")
    private String context;

    @Schema(description = "List of symptoms experienced", example = "[\"Nausea\", \"Stomach ache\"]")
    private ArrayList<String> symptoms;
}