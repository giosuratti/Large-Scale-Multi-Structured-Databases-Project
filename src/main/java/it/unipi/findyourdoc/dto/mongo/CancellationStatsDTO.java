package it.unipi.findyourdoc.dto.mongo;

public record CancellationStatsDTO(
        String specialization,
        Long totalCancelled,
        Double avgPatientAge,   // Aggiunta interessante
        Double avgDoctorRating  // Aggiunta interessante
) {}
