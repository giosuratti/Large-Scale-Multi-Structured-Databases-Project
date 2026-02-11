package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Document(collection = "symptom_reports")
@CompoundIndexes({
        @CompoundIndex(name = "idx_analytics_city_date", def = "{'patientLocation': 1, 'createdAt': 1}"),
        @CompoundIndex(name = "idx_analytics_gender_age", def = "{'patientGender': 1, 'patientAge': 1}")
})
public class SymptomReport extends SymptomReportBrief{
    @Id private String id;
    private int patientAge;
    private String patientGender;
    private Location patientLocation;
}
