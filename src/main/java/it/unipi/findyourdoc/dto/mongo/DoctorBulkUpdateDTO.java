package it.unipi.findyourdoc.dto.mongo;

import it.unipi.findyourdoc.model.mongo.AppointmentDoctor;
import it.unipi.findyourdoc.model.mongo.Location;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for massive doctor synchronization.
 * Contains only the fields required for bulk updates to minimize RAM usage.
 */
@Data
public class DoctorBulkUpdateDTO {
    private String id;
    private String email;
    private String npi;
    private String telephone;
    private Location location;
    private Boolean updated;

    // Initialize lists to prevent NullPointerExceptions
    private List<String> futureAppointments = new ArrayList<>();
    private List<AppointmentDoctor> bookedThisWeek = new ArrayList<>();
}