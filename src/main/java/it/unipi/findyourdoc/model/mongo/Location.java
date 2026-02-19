package it.unipi.findyourdoc.model.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain model representing a physical geographic location.
 * Persisted as an embedded document within Doctors, Patients, and Appointments.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Entity representing the geographic location of a user or office.")
public class Location {

    /** * Detailed street address. */
    private String address;

    /** * Regional or state administrative division. */
    private String state;

    /** * City or municipality name. */
    private String city;

    /** * Postal code used for localized query indexing. */
    private String zipCode;
}