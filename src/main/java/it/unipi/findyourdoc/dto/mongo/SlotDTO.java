package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * Data Transfer Object for a doctor's availability slot.
 * Used to transfer available time slots for medical appointments to the client.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO representing an available time slot for medical appointments.")
public class SlotDTO {

    /** * The specific local date and time when the doctor is available for booking. */
    @Schema(
            description = "Date and time of the availability slot",
            example = "2026-05-20T10:30:00"
    )
    private LocalDateTime dateTime;

}