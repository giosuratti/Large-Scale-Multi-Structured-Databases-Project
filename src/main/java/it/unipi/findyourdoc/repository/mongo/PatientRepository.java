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

    /** * Atomically updates doctor details within a specific embedded appointment.
     * Utilizes the MongoDB positional operator ($) to pinpoint the exact array element.
     */
    @Query("{ '_id': ?0, 'bookedAppointments.appointmentId': ?1 }")
    @Update("{ '$set': { " +
            "  'bookedAppointments.$.location': ?2, " +     // Updates the nested Location object
            "  'bookedAppointments.$.doctorTelephone': ?3 " +  // Updates the nested telephone string
            "} }")
    void updateEmbeddedDoctorData(
            String patientId,
            String appointmentId,
            Location newLocation,
            String newPhone
    );

}