package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Specialized DTO for the booking phase.
 * Combines core professional data with a dynamic list of unbooked time slots.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DTO for reading doctor profile, professional information and available slots")
public class DoctorReadSlotsDTO extends DoctorReadDTOBase {

    /** * Chronological list of timestamps representing the doctor's open availability. */
    @Schema(description = "List of available date-time slots for appointments")
    private ArrayList<LocalDateTime> availableSlots;
}