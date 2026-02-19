package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain model representing a patient's evaluation of a doctor.
 * Typically embedded within the Patient document to maintain a history of provided feedback.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Rating {

    /** * Reference key to the rated professional. */
    private String doctorNpi;

    /** * Denormalized doctor name for display without additional lookups. */
    private String doctorFirstName;
    private String doctorLastName;

    /** * Numeric score (1-5) assigned by the patient. */
    private int rating;
}