package it.unipi.findyourdoc.dto.mongo;

/**
 * Optimized projection for internal synchronization tasks.
 * Links Appointment documents with their embedded references in Patient arrays.
 */
public record AppointmentSyncProjection(

        /** Internal MongoDB ObjectID. */
        String id,

        /** Logical identifier for the specific appointment. */
        String appointmentId,

        /** Reference to the owning Patient document. */
        String patientId,

        /** Reference to the assigned Doctor document. */
        String doctorId
) {}