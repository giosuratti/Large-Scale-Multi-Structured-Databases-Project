package it.unipi.findyourdoc.model.mongo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;

@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentFull extends AppointmentPatient {
    private String id;
    private String patientId;
    private String patientFirstName;
    private String patientLastName;
    private String patientTelephone;
    private ArrayList<String> specialties;
    private float doctorRating;

    private Integer patientAge;
    private String patientGender;

    @CreatedDate
    @Field("created_at")
    private LocalDateTime createdAt;
}
