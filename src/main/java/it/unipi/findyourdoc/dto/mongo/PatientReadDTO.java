package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

/**
 * Comprehensive DTO for retrieving a patient's full profile.
 * Aggregates personal identity, appointment history, reviews, and diagnostic reports.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DTO for reading patient profile information")
public class PatientReadDTO extends UserReadDTO {

    @Schema(description = "The user's first name.", example = "Mario")
    private String firstName;

    @Schema(description = "The user's last name.", example = "Rossi")
    private String lastName;

    /** * Demographic data used for personalized health services. */
    @Schema(description = "The user's age.", example = "25")
    private Integer age;

    @Schema(description = "The user's gender.", example = "Male")
    private String gender;

    /** * Current residential address for localized provider matching. */
    @Schema(description = "The patient's geographic location")
    private LocationDTO location;

    /** * Collection of upcoming and past visits in a lightweight format. */
    @Schema(description = "List of brief summaries of booked appointments")
    private List<AppointmentBriefDTO> bookedAppointments;

    /** * Historic feedback provided by the patient to medical professionals. */
    @Schema(description = "List of ratings given by the patient")
    private List<RatingDTO> ratings;

    /** * Historical symptom submissions used by the diagnostic engine. */
    @Schema(description = "List of recent symptom reports for quick access")
    private List<SymptomReportBriefDTO> recentSymptomReports;
}