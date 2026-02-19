package it.unipi.findyourdoc.dto.neo4j;

/**
 * Projection record used for updating doctor information within the Neo4j graph database.
 * This ensures that specific attributes synchronized between MongoDB and Neo4j are kept consistent.
 *
 * @param npi       The National Provider Identifier used as the unique key in the graph.
 * @param telephone The updated contact number of the doctor.
 * @param city      The updated primary city of practice for location-based graph queries.
 */
public record DoctorGraphUpdateProjection(String npi, String telephone, String city) {}