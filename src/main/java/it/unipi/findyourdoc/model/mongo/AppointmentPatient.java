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
public class AppointmentPatient extends AppointmentBrief{
    private String doctorLastName;
    private String doctorFirstName;
    private String doctorNpi;

    private String doctorEmail;
    private String doctorTelephone;

    private ArrayList<String> doctorSpecialties;

}
