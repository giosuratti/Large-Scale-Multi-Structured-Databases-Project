package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.Location;
import it.unipi.findyourdoc.model.mongo.Patient;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

@Repository
public interface PatientRepository extends UserRepository<Patient> {
    // Questo metodo serve al JwtTokenProvider per validare il paziente
    // boolean existsByEmail(String email);

    // Optional<Patient> findByEmail(String email);

    // void deleteByEmail(String email);

    // Recupera solo l'email

    @Query("{ '_id': ?0, 'bookedAppointments.appointmentId': ?1 }")
    @Update("{ '$set': { " +
            "  'bookedAppointments.$.location': ?2, " +     // Aggiorna l'oggetto Location
            "  'bookedAppointments.$.doctorPhone': ?3 " +  // Aggiorna la stringa Telefono
            "} }")
    void updateEmbeddedDoctorData(
            String patientId,
            String appointmentId,
            Location newLocation,
            String newPhone
    );

}
