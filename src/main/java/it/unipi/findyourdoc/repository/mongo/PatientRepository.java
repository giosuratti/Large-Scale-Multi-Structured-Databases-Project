package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.Patient;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends MongoRepository<Patient, String> {
    // Questo metodo serve al JwtTokenProvider per validare il paziente
    boolean existsByEmail(String email);

    Optional<Patient> findByEmail(String email);
}
