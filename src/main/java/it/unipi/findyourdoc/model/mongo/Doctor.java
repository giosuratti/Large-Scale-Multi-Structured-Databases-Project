package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;

@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "doctors")
public class Doctor extends User {

    private String firstName;
    private String lastName;

    private ArrayList<String> specialties;
    private ArrayList<AppointmentDoctor> bookedThisWeek;
    private ArrayList<LocalDateTime> availableSlots;
    private ArrayList<Integer> ratings;

    private Location location;
    private float avgRating;
    private Integer ratingCount;
    private Integer totalAppointments;
    private String gender;
}
