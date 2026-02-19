package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.SymptomReport;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repository interface for SymptomReport entities in MongoDB.
 * Provides standard CRUD operations for managing detailed health reports submitted by patients.
 */
public interface SymptomReportRepository extends MongoRepository<SymptomReport, String> {
    // This interface inherits all standard MongoDB persistence methods for SymptomReport documents
}