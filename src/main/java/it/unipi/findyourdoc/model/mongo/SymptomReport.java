package it.unipi.findyourdoc.model.mongo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SymptomReport extends SymptomReportBrief{
    @Id private String id;
    private int patientAge;
    private String patientGender;
    private Location patientLocation;
}
