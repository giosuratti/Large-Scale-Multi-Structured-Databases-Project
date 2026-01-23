package it.unipi.findyourdoc.model.mongo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.ArrayList;


@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Patient extends User{

    @Id private String id;


    private String gender;
    private Integer age;

    private String firstName;
    private String lastName;

    private Location location;

    private ArrayList<AppointmentBrief> bookedAppointments;

    private ArrayList<Rating> ratings;
    private ArrayList<SymptomReportBrief> recentSymptomReports;

}
