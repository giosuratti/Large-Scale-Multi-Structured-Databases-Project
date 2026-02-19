package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import it.unipi.findyourdoc.model.mongo.enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Comprehensive DTO for appointment management.
 * Aggregates clinical context, patient identity, and doctor metadata for dashboard visualization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed DTO for an appointment, used for display in dashboards.")
public class AppointmentFullDTO {

    /** * Unique identifier for the appointment document in MongoDB.
     */
    @Schema(description = "The unique ID of the appointment", example = "60d5ecb54f1a2c0015f8e9a1")
    private String id;

    // --- Doctor Metadata ---

    /** * Internal database reference to the doctor.
     */
    @Schema(description = "Internal ID of the doctor", example = "501")
    private String doctorId;

    /** * National Provider Identifier used for cross-referencing with Neo4j.
     */
    @Schema(description = "NPI of the doctor used for profile links and synchronization", example = "47386543")
    private String doctorNpi;

    /** * Scheduled visit timestamp.
     */
    @Schema(description = "Date and time of the appointment", example = "2026-05-15T10:30:00")
    private LocalDateTime DateTime;

    /** * Enum-based lifecycle state.
     */
    @Schema(description = "Current status of the appointment", example = "CONFIRMED")
    private AppointmentStatus status;

    // --- Patient Information ---

    @Schema(description = "First name of the patient", example = "Mario")
    private String patientFirstName;

    @Schema(description = "Last name of the patient", example = "Rossi")
    private String patientLastName;

    @Schema(description = "Email of the patient", example = "name.surname@example.com")
    private String patientEmail;

    @Schema(description = "Contact telephone of the patient", example = "3331234567")
    private String patientTelephone;

    // --- Contextual Details ---

    @Schema(description = "List of medical specialties associated with this appointment", example = "['Cardiology', 'Surgery']")
    private List<String> specialties;

    /** * Snapshot of the doctor's rating at the time of retrieval/display.
     */
    @Schema(description = "Average rating of the doctor", example = "4.8")
    private Double doctorRating;

    /** * Detailed medical office address.
     */
    @Schema(description = "Location of the medical office")
    private LocationDTO location;

}