package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.dto.mongo.DoctorBulkUpdateDTO;
import it.unipi.findyourdoc.dto.mongo.DoctorProjection;
import it.unipi.findyourdoc.dto.mongo.DoctorUpdateProjection;
import it.unipi.findyourdoc.model.mongo.Doctor;
import org.springframework.data.mongodb.repository.Meta;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Repository interface for Doctor entities in MongoDB.
 * Extends UserRepository to inherit common persistence operations.
 */
@Repository
public interface DoctorRepository extends UserRepository<Doctor> {

    /** Retrieves a lightweight projection containing only NPI and rating metrics. */
    @Query(value = "{}", fields = "{ 'npi': 1, 'avgRating': 1, 'ratingCount': 1, '_id': 0 }", hint = "idx_npi_avgRating_ratingCount")
    List<DoctorProjection> findAllBy();

    /** Streams a lightweight projection for memory-efficient rating synchronization. */
    @Query(value = "{}", fields = "{ 'npi': 1, 'avgRating': 1, 'ratingCount': 1, '_id': 0 }", hint = "idx_npi_avgRating_ratingCount")
    @Meta(cursorBatchSize = 1000)
    Stream<DoctorProjection> streamAllBy();

    /** Streams only active doctors requiring synchronization (updated profiles or pending appointments). */
    @Query("{ $or: [ { updated: true }, { 'futureAppointments.0': { $exists: true } }, { 'bookedThisWeek.0': { $exists: true } } ] }")
    @Meta(cursorBatchSize = 2000)
    Stream<DoctorBulkUpdateDTO> streamDoctorsForSync();

    /** Checks if a doctor exists with the specified National Provider Identifier (NPI). */
    boolean existsByNpi(String npi);

    /** Retrieves a doctor based on their unique National Provider Identifier (NPI). */
    Optional<Doctor> findByNpi(String npi);

    /** Retrieves a lightweight projection of doctors pending external synchronization. */
    @Query(value = "{ 'updated': true }", fields = "{ 'npi': 1, 'telephone': 1, 'location': 1, '_id': 0 }")
    List<DoctorUpdateProjection> findAllPendingSyncs();

    /** Atomically marks a doctor as synchronized by resetting the updated flag. */
    @Query("{ 'npi': ?0 }")
    @Update("{ '$set': { 'updated': false } }")
    void markAsSyncedByNpi(String npi);
}