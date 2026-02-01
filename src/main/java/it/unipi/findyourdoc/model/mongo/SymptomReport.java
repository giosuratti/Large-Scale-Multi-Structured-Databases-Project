package it.unipi.findyourdoc.model.mongo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Document(collection = "symptom_reports")
public class SymptomReport extends SymptomReportBrief{
    @Id private String id;
    private int patientAge;
    private String patientGender;
    private Location patientLocation;
}
