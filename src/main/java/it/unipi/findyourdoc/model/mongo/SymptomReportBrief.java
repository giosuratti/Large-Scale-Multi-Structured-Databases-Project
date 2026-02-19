package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Embedded entity representing a summary of a symptom report.
 * Stored within the Patient document to provide a quick history of reported health issues.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SymptomReportBrief {

    /** * Textual description of the circumstances surrounding the symptoms. */
    protected String context;

    /** * List of symptoms identified by the patient or the system. */
    protected ArrayList<String> symptoms;

    /** * List of potential medical conditions identified based on the provided symptoms. */
    protected ArrayList<String> possibleDiagnosies;

    /** * Automatically generated timestamp of when the report was submitted. */
    @CreatedDate
    protected LocalDateTime createdAt;

}