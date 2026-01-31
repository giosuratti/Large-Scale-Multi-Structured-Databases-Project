package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class SymptomReportBrief {
    protected String context;
    protected ArrayList<String> symptoms;
    protected ArrayList<String> possibleDiagnosies;

    @CreatedDate
    protected LocalDateTime createdAt;

}
