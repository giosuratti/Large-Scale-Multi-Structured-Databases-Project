package it.unipi.findyourdoc.dto.mongo;

/**
 * Lightweight projection for doctor reputation data.
 * Used for high-performance updates and synchronization of rating metrics.
 */
public record DoctorProjection(
        /** * Unique National Provider Identifier used as the primary key for lookups. */
        String npi,

        /** * Calculated mean of all patient reviews. */
        Double avgRating,

        /** * Cumulative number of ratings received by the professional. */
        Integer ratingCount
) {}