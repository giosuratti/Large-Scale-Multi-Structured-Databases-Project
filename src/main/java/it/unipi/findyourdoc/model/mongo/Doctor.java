package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;
import java.util.ArrayList;

@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Doctor extends User {

    private String firstName;
    private String lastName;

    private ArrayList<String> specialties;
    private ArrayList<AppointmentDoctor> bookedThisWeek;
    private ArrayList<LocalDateTime> availableSlots;
    private ArrayList<Integer> rating;

    private Location location;
    private float avgRating;
    private Integer ratingCount;
    private Integer totalAppointments;
    private String gender;
}
