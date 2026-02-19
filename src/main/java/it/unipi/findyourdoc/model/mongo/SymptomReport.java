package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Persistent entity for diagnostic sessions.
 * Captures user-reported symptoms and system-inferred diagnoses along with
 * demographic metadata for epidemiological analysis.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Document(collection = "symptom_reports")
@CompoundIndexes({
        /** * Optimizes regional health trend queries by location and timeframe. */
        @CompoundIndex(name = "idx_analytics_city_date", def = "{'patientLocation': 1, 'createdAt': 1}"),
        /** * Optimizes demographic-based analysis for age and gender distribution. */
        @CompoundIndex(name = "idx_analytics_gender_age", def = "{'patientGender': 1, 'patientAge': 1}")
})
public class SymptomReport extends SymptomReportBrief {

    /** * Unique identifier for the specific diagnostic report. */
    @Id
    private String id;

    /** * Patient's age at the time of the report, used for age-group filtering. */
    private int patientAge;

    /** * Patient's gender, used to identify gender-specific diagnostic trends. */
    private String patientGender;

    /** * Snapshot of the patient's location to track geographic symptom clusters. */
    private Location patientLocation;
}