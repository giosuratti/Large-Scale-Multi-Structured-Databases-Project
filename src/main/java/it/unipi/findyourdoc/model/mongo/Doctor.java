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
public class Doctor extends User {

    @Id private String id;
    private String firstName;
    private String lastName;

    private ArrayList<String> specializations;
    private ArrayList<AppointmentDoctor> bookedToday;
    private ArrayList<Slot> availableSlots;
    private ArrayList<Integer> rating;

    private Location location;
    private float avgRating;
    private Integer ratingCount;
    private Integer totalAppointments;
    private String gender;
}
