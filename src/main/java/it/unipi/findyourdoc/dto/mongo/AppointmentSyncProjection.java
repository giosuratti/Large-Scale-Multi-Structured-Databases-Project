package it.unipi.findyourdoc.dto.mongo;

public record AppointmentSyncProjection(
        String id,            // ID interno Mongo (utile per sicurezza)
        String appointmentId, // ID logico (per trovare l'item nell'array del paziente)
        String patientId
) {
}
