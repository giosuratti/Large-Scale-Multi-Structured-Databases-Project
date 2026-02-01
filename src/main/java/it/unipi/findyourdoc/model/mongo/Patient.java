package it.unipi.findyourdoc.model.mongo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;


@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "patients")
public class Patient extends User{

    private String gender;
    private Integer age;

    private String firstName;
    private String lastName;

    private Location location;

    private ArrayList<AppointmentPatient> bookedAppointments;

    private ArrayList<Rating> ratings;
    private ArrayList<SymptomReportBrief> recentSymptomReports;

}
