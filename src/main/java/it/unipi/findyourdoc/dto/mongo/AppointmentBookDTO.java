package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import it.unipi.findyourdoc.model.mongo.Location;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO for booking an appointment.")
public class AppointmentBookDTO {
    private String patientEmail;
    private String patientTelephone;
    private String patientName;
    private String patientSurname;
    private LocalDateTime dateTime;

    private String doctorEmail;
    private String doctorTelephone;
    private String doctorName;
    private String doctorSurname;
    private String doctorAvgRating; //per Analytics
    private Location location;

}
