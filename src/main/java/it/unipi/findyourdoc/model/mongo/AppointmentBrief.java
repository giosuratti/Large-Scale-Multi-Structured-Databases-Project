package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class AppointmentBrief {
    @Id protected int appointmentId;
    protected LocalDateTime date;
    protected Location location;
    protected AppointmentStatus status;
}
