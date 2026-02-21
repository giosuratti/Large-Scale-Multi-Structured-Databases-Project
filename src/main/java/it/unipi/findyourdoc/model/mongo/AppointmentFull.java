package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Entity representing a complete appointment record in the MongoDB "appointments" collection.
 * This class extends {@link AppointmentBrief} and contains denormalized patient and doctor
 * information to optimize read operations and maintain historical data.
 */
@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "appointments")
@CompoundIndex(
        name = "doctor_scheduled_partial_idx",
        def = "{'doctorId': 1}",
        partialFilter = "{'status': 'SCHEDULED'}"
)
public class AppointmentFull extends AppointmentBrief {

    /** * Unique identifier for the appointment, mapped to MongoDB's ObjectId. */
    @MongoId(FieldType.OBJECT_ID) // Correct for root document
    private String appointmentId;

    /** * Reference to the patient's record. */
    @Field(targetType = FieldType.OBJECT_ID)
    private String patientId;

    /** * Reference to the doctor's record, indexed for faster query performance. */
    @Field(targetType = FieldType.OBJECT_ID)
    private String doctorId;

    private String patientFirstName;
    private String patientLastName;
    private String patientTelephone;
    private String patientEmail;

    /** * List of doctor's specialties at the time of the appointment. */
    private ArrayList<String> specialties;

    /** * Rating assigned by the patient after the visit. */
    private Double doctorRating;

    private Integer patientAge;
    private String patientGender;

    /** * Automatically populated timestamp when the document is first created. */
    @CreatedDate
    private LocalDateTime createdAt;
}