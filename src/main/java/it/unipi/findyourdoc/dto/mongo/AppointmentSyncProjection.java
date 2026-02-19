package it.unipi.findyourdoc.dto.mongo;

/**
 * Optimized projection for internal synchronization tasks.
 * Used to efficiently link Appointment documents with their embedded references in Patient arrays.
 */
public record AppointmentSyncProjection(
        /** * Internal MongoDB ObjectID for precise document mapping. */
        String id,

        /** * Logical identifier used to locate the specific appointment within the Patient's array. */
        String appointmentId,

        /** * Reference to the Patient document owning this appointment. */
        String patientId
) {
}