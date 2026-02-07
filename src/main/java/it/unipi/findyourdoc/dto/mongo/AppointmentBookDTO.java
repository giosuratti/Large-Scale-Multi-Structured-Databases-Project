package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import it.unipi.findyourdoc.model.mongo.Location;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO used for the booking process of an appointment.")
public class AppointmentBookDTO {

    // --- Dati del Paziente ---
    @Schema(description = "Email of the patient", example = "mario.rossi@example.com")
    private String patientEmail;

    @Schema(description = "Contact telephone of the patient", example = "3331234567")
    private String patientTelephone;

    @Schema(description = "First name of the patient", example = "Mario")
    private String patientName;

    @Schema(description = "Last name of the patient", example = "Rossi")
    private String patientSurname;

    // --- Dettagli Appuntamento ---
    @Schema(description = "Scheduled date and time for the appointment", example = "2026-06-20T15:30:00")
    private ZonedDateTime dateTime;

    // --- Dati del Dottore ---
    @Schema(description = "Email of the doctor", example = "dr.bianchi@hospital.com")
    private String doctorEmail;

    @Schema(description = "Contact telephone of the doctor", example = "0212345678")
    private String doctorTelephone;

    @Schema(description = "First name of the doctor", example = "Giulia")
    private String doctorFirstName;

    @Schema(description = "Last name of the doctor", example = "Bianchi")
    private String doctorLastName;

    @Schema(description = "Snapshot of doctor's average rating at booking time", example = "4.5")
    private String doctorAvgRating;

    @Schema(description = "Snapshot of doctor's total ratings at booking time", example = "2")
    private String doctorRatingCount;

    @Schema(description = "Location object containing address details")
    private Location location;

    @Schema(description = "Doctor's id", example = "47386543")
    private String doctorId;

    @Schema(description = "Doctor's npi", example = "47386543")
    private String doctorNpi;
}
