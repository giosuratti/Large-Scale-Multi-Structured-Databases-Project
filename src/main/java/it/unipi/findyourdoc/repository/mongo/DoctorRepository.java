package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.Doctor;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DoctorRepository extends MongoRepository<Doctor, String> {
    // Questo metodo serve al JwtTokenProvider per validare il dottore
    boolean existsByEmail(String email);

    Optional<Doctor> findByEmail(String email);
}