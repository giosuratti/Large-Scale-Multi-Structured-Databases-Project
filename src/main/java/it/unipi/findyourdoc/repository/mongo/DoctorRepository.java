package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.dto.mongo.DoctorProjection;
import it.unipi.findyourdoc.model.mongo.Doctor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface DoctorRepository extends UserRepository<Doctor>{
    // Questo metodo serve al JwtTokenProvider per validare il dottore
    // boolean existsByEmail(String email);
    // void deleteByEmail(String email);
    List<DoctorProjection> findAllBy();
    boolean existsByNpi(String npi);
    Optional<Doctor> findByNpi(String npi);
    // Optional<Doctor> findByEmail(String email);
}