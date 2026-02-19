package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;

/**
 * Entity representing a Patient within the MongoDB "patients" collection.
 * Extends {@link User} with personal profile details, location, and medical history summaries.
 */
@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "patients")
public class Patient extends User {

    private String gender;
    private Integer age;

    private String firstName;
    private String lastName;

    /** * Current geographic location of the patient for proximity-based searches. */
    private Location location;

    /** * List of denormalized appointment summaries specifically for the patient's view. */
    private ArrayList<AppointmentPatient> bookedAppointments;

    /** * History of ratings and feedback provided by the patient to doctors. */
    private ArrayList<Rating> ratings;

    /** * Brief summaries of the most recent symptom reports submitted by the patient. */
    private ArrayList<SymptomReportBrief> recentSymptomReports;

}