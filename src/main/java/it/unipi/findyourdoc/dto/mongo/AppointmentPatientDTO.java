package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import it.unipi.findyourdoc.model.mongo.enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * DTO for the patient-facing view of an appointment.
 * Optimized for the "My Appointments" personal dashboard.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Synthetic appointment DTO for patient-side visualization.")
public class AppointmentPatientDTO {

    /** * Unique identifier for the appointment document. */
    @Schema(description = "Unique identifier of the appointment", example = "60d5ecb54f1a2c0015f8e9a1")
    private String id;

    /** * Scheduled visit timestamp. */
    @Schema(description = "Date and time of the appointment", example = "2026-05-15T10:30:00")
    private LocalDateTime dateTime;

    /** * Current lifecycle state. */
    @Schema(description = "Status of the appointment (e.g., PENDING, CONFIRMED, CANCELLED)", example = "CONFIRMED")
    private AppointmentStatus status;

    // --- Doctor Metadata ---

    /** * National Provider Identifier used for profile cross-linking. */
    @Schema(description = "Doctor's NPI (useful for profile linking)", example = "47386543")
    private String doctorNpi;

    @Schema(description = "Doctor's first name", example = "Giulia")
    private String doctorFirstName;

    @Schema(description = "Doctor's last name", example = "Bianchi")
    private String doctorLastName;

    @Schema(description = "Doctor's medical specialties", example = "['Cardiology']")
    private ArrayList<String> doctorSpecialties;

    // --- Facility Details ---

    /** * Medical office physical address. */
    @Schema(description = "Address of the medical office")
    private LocationDTO location;
}