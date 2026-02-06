package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class AppointmentBrief {
    @Field(targetType = FieldType.OBJECT_ID)
    protected String appointmentId;
    protected LocalDateTime dateTime;
    protected Location location;
    protected AppointmentStatus status;
}
