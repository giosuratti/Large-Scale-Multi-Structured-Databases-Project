package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Specialized brief representation of an appointment for the Doctor's perspective.
 * Denormalizes patient contact information to allow immediate access from the doctor's agenda.
 */
@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentDoctor extends AppointmentBrief {

    /** * Denormalized first name of the patient for quick UI rendering. */
    private String patientFirstName;

    /** * Denormalized last name of the patient. */
    private String patientLastName;

    /** * Direct contact number to facilitate urgent communication or reminders. */
    private String patientTelephone;
}