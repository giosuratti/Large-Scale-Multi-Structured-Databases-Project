package it.unipi.findyourdoc.model.mongo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentFull extends AppointmentPatient {
    private String patientFirstName;
    private String patientLastName;
    private String patientTelephone;
    private ArrayList<String> specialties;
    private float doctorRating;
}
