package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Lightweight DTO for appointment overviews.
 * Designed for high-performance list rendering and mobile-friendly responses.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Brief summary of an appointment for list views")
public class AppointmentBriefDTO {

    /** * Unique identifier for the appointment record.
     */
    @Schema(description = "The unique identifier of the appointment", example = "101")
    private int appointmentId;

    /** * Local timestamp of the visit.
     */
    @Schema(description = "Date and time of the appointment", example = "2024-05-20T15:30:00")
    private LocalDateTime date;

    /** * Summary of the medical facility address.
     */
    @Schema(description = "The location where the appointment takes place")
    private LocationDTO location;

    /** * Lifecycle state of the appointment (e.g., BOOKED, COMPLETED, CANCELLED).
     * Represented as a String for broader JSON compatibility.
     */
    @Schema(description = "The current status of the appointment", example = "BOOKED")
    private String status;
}