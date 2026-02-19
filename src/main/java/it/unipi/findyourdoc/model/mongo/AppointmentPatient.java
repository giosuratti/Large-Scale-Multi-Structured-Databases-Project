package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

/**
 * Specialized brief representation of an appointment for the Patient's perspective.
 * Denormalizes doctor profile and contact data to provide a self-contained history/agenda item.
 */
@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentPatient extends AppointmentBrief {

    /** * Denormalized doctor name for immediate display in the patient's dashboard. */
    private String doctorLastName;
    private String doctorFirstName;

    /** * Cross-reference key (NPI) for linking back to the full Doctor document. */
    private String doctorNpi;

    /** * Cached contact details to allow patients to reach the clinic directly from their agenda. */
    private String doctorEmail;
    private String doctorTelephone;

    /** * Relevant medical expertise of the professional at the time of booking. */
    private ArrayList<String> doctorSpecialties;
}