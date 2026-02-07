package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.dto.mongo.AppointmentFullSummary;
import it.unipi.findyourdoc.model.mongo.AppointmentFull;
import it.unipi.findyourdoc.model.mongo.enums.AppointmentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends MongoRepository<AppointmentFull, String> {

    boolean existsByDoctorIdAndDateTime(String id, LocalDateTime dateTime);

    List<AppointmentFull> findByPatientIdOrderByDateTimeDesc(String patientId);

    List<AppointmentFull> findByDoctorId(String Id);

    List<AppointmentFull> findByDoctorIdAndDateTimeAfter(String doctorId, LocalDateTime now);

    List<AppointmentFull> findByPatientIdAndDateTimeAfter(String patientId, LocalDateTime now);

    // Recupera solo i campi definiti nel record
    @Query(value = "{ '_id': ?0 }", fields = "{ 'appointmentId': 1, 'doctorId': 1, 'patientId': 1, 'dateTime': 1, 'status': 1 }")
    Optional<AppointmentFullSummary> findSummaryByAppointmentId(String id);

    // Aggiorna lo stato senza scaricare tutto l'oggetto
    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'status': 'CANCELLED' } }")
    void updateStatusToCancelled(String id);

    @Query(value = "{ '_id': ?0 }", fields = "{ 'appointmentId': 1, 'doctorId': 1, 'patientId': 1, 'dateTime': 1, 'status': 1 }")
    Optional<AppointmentFullSummary> findSummaryById(String appointmentId);

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'status': ?1 } }")
    void updateStatus(String id, AppointmentStatus status);

}
