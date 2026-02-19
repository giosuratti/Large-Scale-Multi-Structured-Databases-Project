package it.unipi.findyourdoc.dto.mongo;

/**
 * DTO for analytical insights into appointment cancellations.
 * Aggregates data by medical specialization to identify platform trends and potential friction points.
 */
public record CancellationStatsDTO(
        /** * The medical field (e.g., Cardiology, Dermatology) being analyzed. */
        String specialization,

        /** * Cumulative count of cancelled appointments for this specialization. */
        Long totalCancelled,

        /** * Demographic insight: Average age of patients who cancel in this category. */
        Double avgPatientAge,

        /** * Quality insight: Average rating of doctors whose appointments are being cancelled. */
        Double avgDoctorRating
) {}