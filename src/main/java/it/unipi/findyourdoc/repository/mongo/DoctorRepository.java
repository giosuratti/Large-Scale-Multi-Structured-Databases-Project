package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.dto.mongo.DoctorProjection;
import it.unipi.findyourdoc.dto.mongo.DoctorUpdateProjection;
import it.unipi.findyourdoc.model.mongo.Doctor;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
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

    // 1. LEGGE LE PROIEZIONI (Escludiamo _id per pulizia)
    @Query(value = "{ 'updated': true }", fields = "{ 'npi': 1, 'telephone': 1, 'location': 1, '_id': 0 }")
    List<DoctorUpdateProjection> findAllPendingSyncs();

    // 2. RESETTA IL FLAG USANDO L'NPI
    @Query("{ 'npi': ?0 }")
    @Update("{ '$set': { 'updated': false } }")
    void markAsSyncedByNpi(String npi);
}