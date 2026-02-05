package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.Patient;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientRepository extends UserRepository<Patient> {
    // Questo metodo serve al JwtTokenProvider per validare il paziente
    // boolean existsByEmail(String email);

    // Optional<Patient> findByEmail(String email);

    // void deleteByEmail(String email);
}
