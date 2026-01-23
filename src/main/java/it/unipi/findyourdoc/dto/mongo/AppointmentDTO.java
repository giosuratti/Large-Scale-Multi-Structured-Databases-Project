package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import it.unipi.findyourdoc.model.mongo.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing full appointment details, merging patient and doctor context.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed DTO for an appointment, used for display in dashboards.")
public class AppointmentDTO {

    @Schema(description = "The unique ID of the appointment", example = "1024")
    private String id;

    @Schema(description = "Date and time of the appointment", example = "2026-05-15T10:30:00")
    private LocalDateTime appointmentDateTime;

    @Schema(description = "Current status of the appointment", example = "CONFIRMED")
    private AppointmentStatus status;

    // --- Dati del Paziente (dalla tua classe AppointmentFull) ---
    @Schema(description = "First name of the patient", example = "Mario")
    private String patientFirstName;

    @Schema(description = "Last name of the patient", example = "Rossi")
    private String patientLastName;

    @Schema(description = "Contact telephone of the patient", example = "3331234567")
    private String patientTelephone;

    // --- Dati del Dottore (ereditati concettualmente) ---
    @Schema(description = "First name of the doctor", example = "Giulia")
    private String doctorFirstName;

    @Schema(description = "Last name of the doctor", example = "Bianchi")
    private String doctorLastName;

    @Schema(description = "List of doctor's specialties", example = "['Cardiology', 'Surgery']")
    private List<String> specialties;

    @Schema(description = "Average rating of the doctor", example = "4.8")
    private float doctorRating;

    @Schema(description = "Location of the medical office")
    private LocationDTO location;
}