package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.SymptomReport;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SymptomReportRepository extends MongoRepository<SymptomReport, String> {
}
