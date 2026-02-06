package it.unipi.findyourdoc.model.mongo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.time.LocalDateTime;
import java.util.ArrayList;

@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "appointments")
public class AppointmentFull extends AppointmentBrief{
    @Field(targetType = FieldType.OBJECT_ID)
    private String patientId;
    @Field(targetType = FieldType.OBJECT_ID)
    @Indexed
    private String doctorId;
    private String patientFirstName;
    private String patientLastName;
    private String patientTelephone;
    private ArrayList<String> specialties;
    private float doctorRating;

    private Integer patientAge;
    private String patientGender;

    @CreatedDate
    private LocalDateTime createdAt;
}
