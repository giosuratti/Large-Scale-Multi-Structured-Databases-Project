package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import it.unipi.findyourdoc.model.mongo.Location;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * DTO representing the required data to finalize an appointment booking.
 * Captures snapshots of both patient and doctor information to ensure historical consistency.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO used for the booking process of an appointment.")
public class AppointmentBookDTO {

    // --- Patient Information ---
    @Schema(description = "Email of the patient", example = "mario.rossi@example.com")
    private String patientEmail;

    @Schema(description = "Contact telephone of the patient", example = "+39 3331234567")
    private String patientTelephone;

    @Schema(description = "First name of the patient", example = "Mario")
    private String patientName;

    @Schema(description = "Last name of the patient", example = "Rossi")
    private String patientSurname;

    // --- Appointment Details ---
    @Schema(description = "Scheduled date and time for the appointment", example = "2026-06-20T15:30:00Z")
    private ZonedDateTime dateTime;

    // --- Doctor Information ---
    @Schema(description = "Email of the doctor", example = "dr.bianchi@hospital.com")
    private String doctorEmail;

    @Schema(description = "Contact telephone of the doctor", example = "+39 0212345678")
    private String doctorTelephone;

    @Schema(description = "First name of the doctor", example = "Giulia")
    private String doctorFirstName;

    @Schema(description = "Last name of the doctor", example = "Bianchi")
    private String doctorLastName;

    // Captures the reputation of the doctor at the specific moment of booking.
    @Schema(description = "Snapshot of doctor's average rating at booking time", example = "4.5")
    private String doctorAvgRating;

    @Schema(description = "Snapshot of doctor's total ratings at booking time", example = "120")
    private String doctorRatingCount;

    // Embedded object representing the physical site of the medical visit.
    @Schema(description = "Location object containing address details")
    private Location location;

    @Schema(description = "Unique MongoDB identifier for the doctor", example = "60d5ecb54f1a2c0015f8e9a1")
    private String doctorId;

    @Schema(description = "National Provider Identifier (NPI) used for cross-database synchronization", example = "47386543")
    private String doctorNpi;
}