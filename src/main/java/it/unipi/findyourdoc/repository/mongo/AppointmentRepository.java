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

/**
 * Data Access Object for Appointment persistence in MongoDB.
 * Implements optimized query projections and bulk update operations for calendar consistency.
 */
@Repository
public interface AppointmentRepository extends MongoRepository<AppointmentFull, String> {

    /** * Checks for scheduling conflicts by identifying existing slots for a specific doctor. */
    boolean existsByDoctorIdAndDateTime(String id, LocalDateTime dateTime);

    List<AppointmentFull> findByPatientIdOrderByDateTimeDesc(String patientId);

    List<AppointmentFull> findByDoctorId(String Id);

    /** * Retrieves upcoming appointments for a doctor based on a specific cutoff time. */
    List<AppointmentFull> findByDoctorIdAndDateTimeAfter(String doctorId, LocalDateTime now);

    List<AppointmentFull> findByPatientIdAndDateTimeAfter(String patientId, LocalDateTime now);

    /** * Optimized projection that retrieves only essential metadata via field exclusion. */
    @Query(value = "{ '_id': ?0 }", fields = "{ 'appointmentId': 1, 'doctorId': 1, 'patientId': 1, 'dateTime': 1, 'status': 1 }")
    Optional<AppointmentFullSummary> findSummaryByAppointmentId(String id);

    /** * In-place update to cancel an appointment without loading the full document into memory. */
    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'status': 'CANCELLED' } }")
    void updateStatusToCancelled(String id);

    @Query(value = "{ '_id': ?0 }", fields = "{ 'appointmentId': 1, 'doctorId': 1, 'patientId': 1, 'dateTime': 1, 'status': 1 }")
    Optional<AppointmentFullSummary> findSummaryById(String appointmentId);

    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'status': ?1 } }")
    void updateStatus(String id, AppointmentStatus status);

    /** * Fetches IDs of future active appointments to facilitate cross-document synchronization. */
    @Query(
            value = "{ 'doctorNpi': ?0, 'dateTime': { $gt: new Date() }, 'status': { $in: ['SCHEDULED', 'PENDING', 'RESCHEDULED'] } }",
            fields = "{ 'appointmentId': 1, 'patientId': 1 }"
    )
    List<AppointmentSyncProjection> findFutureSummariesByDoctorNpi(String doctorNpi);

    /** * Executes an atomic bulk update for all future appointments of a specific doctor. */
    @Query("{ 'doctorNpi': ?0, 'dateTime': { $gt: new Date() }, 'status': { $in: ['SCHEDULED', 'PENDING', 'RESCHEDULED'] } }")
    @Update("{ '$set': { 'location': ?1} }")
    void updateFutureAppointmentsDataBulk(String doctorNpi, Location newLocation);

    @Query("{ '_id' : { $in : ?0 } }")
    @Update("{ '$set' : { 'location' : ?1, 'doctorTelephone' : ?2 } }")
    void updateDoctorInfoForIds(List<String> ids, Location location, String doctorTelephone);

    @Query("{ 'doctorId' : ?0, 'status' : 'SCHEDULED' }")
    @Update("{ '$set' : { 'location' : ?1, 'doctorTelephone' : ?2 } }")
    void updateDoctorInfoBulk(String doctorId, Location location, String doctorTelephone);

    /** * Lightweight projection used to identify which patient documents require synchronization updates. */
    @Query(value = "{ 'doctorId' : ?0, 'status' : 'SCHEDULED' }", fields = "{ 'patientId' : 1, '_id' : 1 }")
    List<AppointmentSyncProjection> findFuturePatientIdsByDoctorId(String doctorId);
}