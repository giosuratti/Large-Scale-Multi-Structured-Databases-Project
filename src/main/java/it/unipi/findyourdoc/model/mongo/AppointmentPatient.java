package it.unipi.findyourdoc.model.mongo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@EqualsAndHashCode(callSuper = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentPatient extends AppointmentBrief{
    protected String doctorLastName;
    protected String doctorFirstName;
    @Field(targetType = FieldType.OBJECT_ID)
    protected String doctorId;
}
