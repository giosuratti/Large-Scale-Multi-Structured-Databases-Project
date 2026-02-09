package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * Data Transfer Object for a doctor's availability slot.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO representing an available time slot for medical appointments.")
public class SlotDTO {

    @Schema(
            description = "Date and time of the availability slot",
            example = "2026-05-20T10:30:00"
    )
    private LocalDateTime dateTime;

}