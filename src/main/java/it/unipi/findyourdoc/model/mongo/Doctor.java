package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Entity representing a Doctor within the MongoDB "doctors" collection.
 * Extends {@link User} with professional-specific attributes, availability, and ratings.
 */
@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "doctors")
@CompoundIndexes({
        // Optimized index for filtering and sorting by NPI and rating metrics
        @CompoundIndex(name = "idx_npi_avgRating_ratingCount", def = "{'npi': 1, 'avgRating': 1, 'ratingCount': 1}")
})
public class Doctor extends User {

    private String firstName;
    private String lastName;

    /** * National Provider Identifier, unique across the system to identify the professional. */
    @Indexed(unique = true)
    private String npi;

    private ArrayList<String> specialties;

    /** * List of appointment summaries for the current week to manage short-term scheduling. */
    private ArrayList<AppointmentDoctor> bookedThisWeek;

    /** * List of available time slots where patients can book new appointments. */
    private ArrayList<LocalDateTime> availableSlots;

    /** * Raw list of individual rating scores given by patients. */
    private ArrayList<Integer> ratings;

    private Location location;

    /** * Denormalized average rating for high-performance sorting and searching. */
    private Double avgRating;

    /** * Total number of ratings received to calculate the average. */
    private Integer ratingCount;

    private String gender;

    /** * Flag used to track if the profile has been updated for synchronization with other services. */
    private Boolean updated;

    /** * List of IDs or references for appointments scheduled beyond the current week. */
    private ArrayList<String> futureAppointments;
}