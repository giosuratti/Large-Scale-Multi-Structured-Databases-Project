package it.unipi.findyourdoc.model.mongo;

import it.unipi.findyourdoc.model.mongo.enums.AppointmentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.LocalDateTime;

/**
 * Base abstract class for lightweight appointment data.
 * Used for embedding appointment summaries within other documents to minimize cross-collection lookups.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class AppointmentBrief {

    /** * Reference to the primary Appointment document ID in MongoDB. */
    @Field(targetType = FieldType.OBJECT_ID)
    protected String appointmentId;

    /** * Scheduled date and time for the medical encounter. */
    protected LocalDateTime dateTime;

    /** * Physical location where the appointment takes place. */
    protected Location location;

    /** * Current lifecycle state of the appointment (e.g., SCHEDULED, COMPLETED). */
    protected AppointmentStatus status;
}