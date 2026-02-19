package it.unipi.findyourdoc.dto.mongo;

import it.unipi.findyourdoc.model.mongo.enums.AppointmentStatus;
import java.time.LocalDateTime;

/**
 * Optimized Projection (Record) for appointment lifecycle management.
 * Contains only the essential fields required for validation and cancellation logic.
 */
public record AppointmentFullSummary(
        /** * Unique identifier for the appointment document. */
        String appointmentId,

        /** * Internal reference to the assigned doctor. */
        String doctorId,

        /** * Internal reference to the booking patient. */
        String patientId,

        /** * Visit timestamp used for "time-remaining" validation (e.g., 24h notice). */
        LocalDateTime dateTime,

        /** * Current state used to verify if cancellation is still possible. */
        AppointmentStatus status
) {}