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
import java.util.Set;

/**
 * Data Access Object for Appointment persistence in MongoDB.
 * Implements optimized query projections and bulk update operations for calendar consistency.
 */
@Repository
public interface AppointmentRepository extends MongoRepository<AppointmentFull, String> {

    /** Checks for scheduling conflicts by identifying existing slots for a specific doctor. */
    boolean existsByDoctorIdAndDateTime(String id, LocalDateTime dateTime);

    /** Retrieves upcoming appointments for a doctor based on a specific cutoff time. */
    List<AppointmentFull> findByDoctorIdAndDateTimeAfter(String doctorId, LocalDateTime now);

    /** Retrieves upcoming appointments for a patient based on a specific cutoff time. */
    List<AppointmentFull> findByPatientIdAndDateTimeAfter(String patientId, LocalDateTime now);


    /** Alternative summary retrieval by internal appointment ID. */
    @Query(value = "{ '_id': ?0 }", fields = "{ 'appointmentId': 1, 'doctorId': 1, 'patientId': 1, 'dateTime': 1, 'status': 1 }")
    Optional<AppointmentFullSummary> findSummaryById(String appointmentId);

    /** In-place atomic update to modify an appointment's status. */
    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'status': ?1 } }")
    void updateStatus(String id, AppointmentStatus status);


    /** Lightweight projection used to identify which single patient document requires sync updates. */
    @Query(value = "{ 'doctorId' : ?0, 'status' : 'SCHEDULED' }", fields = "{ 'patientId' : 1, '_id' : 1 }")
    List<AppointmentSyncProjection> findFuturePatientIdsByDoctorId(String doctorId);

    /** * Mass pre-fetching projection for sync targets across multiple doctors simultaneously.
     * Note: Redis can handle tracking these state arrays for you to avoid this DB hit entirely if read volume grows.
     */
    @Query(value = "{ 'doctorId': { $in: ?0 }, 'dateTime': { $gte: new Date() } }", fields = "{ 'doctorId': 1, 'patientId': 1, '_id': 1 }")
    List<AppointmentSyncProjection> findFuturePatientIdsByDoctorIdIn(Set<String> doctorIds);
}