package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.Location;
import it.unipi.findyourdoc.model.mongo.Patient;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

/**
 * Data Access Object for Patient persistence in MongoDB.
 * Manages patient-specific queries and complex in-place updates for denormalized arrays.
 */
@Repository
public interface PatientRepository extends UserRepository<Patient> {

    // Common identity validations (like existsByEmail) are inherited from UserRepository.

}