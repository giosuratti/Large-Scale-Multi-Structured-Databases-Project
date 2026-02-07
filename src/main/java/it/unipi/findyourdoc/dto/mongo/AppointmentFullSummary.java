package it.unipi.findyourdoc.dto.mongo; // O il package che preferisci per i DTO/Projections

import it.unipi.findyourdoc.model.mongo.enums.AppointmentStatus;

import java.time.LocalDateTime;

/**
 * Record per la lettura ottimizzata (Projection) dell'appuntamento.
 * Include solo i campi necessari alla logica di cancellazione.
 */
public record AppointmentFullSummary(
        String appointmentId,
        String doctorId,
        String patientId,
        LocalDateTime dateTime,
        AppointmentStatus status
) {}