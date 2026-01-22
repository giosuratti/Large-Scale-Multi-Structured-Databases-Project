package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Brief summary of an appointment for list views")
public class AppointmentBriefDTO {

    @Schema(description = "The unique identifier of the appointment", example = "101")
    private int appointmentId;

    @Schema(description = "Date and time of the appointment", example = "2024-05-20T15:30:00")
    private LocalDateTime date;

    @Schema(description = "The location where the appointment takes place")
    private LocationDTO location;

    @Schema(description = "The current status of the appointment", example = "BOOKED")
    private String status;
    // Usiamo String per lo status nel DTO per semplicità di lettura JSON
}
