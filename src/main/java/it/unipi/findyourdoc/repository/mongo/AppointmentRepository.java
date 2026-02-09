package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.dto.mongo.AppointmentFullSummary;
import it.unipi.findyourdoc.dto.mongo.AppointmentSyncProjection;
import it.unipi.findyourdoc.model.mongo.AppointmentFull;
import it.unipi.findyourdoc.model.mongo.Location;
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

    @Query("{ 'doctorNpi': ?0, " +
            "  'dateTime': { $gt: new Date() }, " +
            "  'status': { $in: ['SCHEDULED', 'PENDING', 'RESCHEDULED'] } }")
    List<AppointmentFull> findFutureAppointmentsByDoctorNpi(String doctorNpi);

    // 1. LEGGE SOLO I CAMPI NECESSARI (Proiezione)
    @Query(
            value = "{ 'doctorNpi': ?0, 'dateTime': { $gt: new Date() }, 'status': { $in: ['SCHEDULED', 'PENDING', 'RESCHEDULED'] } }",
            fields = "{ 'appointmentId': 1, 'patientId': 1 }" // Scarica SOLO questi campi!
    )
    List<AppointmentSyncProjection> findFutureSummariesByDoctorNpi(String doctorNpi);

    // 2. AGGIORNAMENTO MASSIVO (Bulk Update)
    // Aggiorna Location e Telefono di TUTTI gli appuntamenti futuri in una sola query atomica.
    @Query("{ 'doctorNpi': ?0, 'dateTime': { $gt: new Date() }, 'status': { $in: ['SCHEDULED', 'PENDING', 'RESCHEDULED'] } }")
    @Update("{ '$set': { 'location': ?1} }")
    void updateFutureAppointmentsDataBulk(String doctorNpi, Location newLocation);

}
