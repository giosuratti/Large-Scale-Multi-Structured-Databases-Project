package it.unipi.findyourdoc.dto.mongo;

import it.unipi.findyourdoc.model.mongo.Location;

/**
 * Lightweight projection used during doctor profile updates.
 * Captures core contact and spatial data required for cross-platform synchronization.
 */
public record DoctorUpdateProjection(
        /** * National Provider Identifier used as the primary lookup key. */
        String npi,

        /** * Current professional telephone number for contact updates. */
        String telephone,

        /** * Geographical and address data used for re-indexing in search engines. */
        Location location
) {
}