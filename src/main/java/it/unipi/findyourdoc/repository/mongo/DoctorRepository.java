package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.dto.mongo.DoctorProjection;
import it.unipi.findyourdoc.dto.mongo.DoctorUpdateProjection;
import it.unipi.findyourdoc.model.mongo.Doctor;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Doctor entities in MongoDB.
 * Extends {@link UserRepository} to inherit common user persistence operations.
 */
@Repository
public interface DoctorRepository extends UserRepository<Doctor>{

    /** * Retrieves a lightweight projection of all doctors containing only NPI and rating metrics.
     * Uses the 'idx_npi_avgRating_ratingCount' hint to force MongoDB to use the optimized compound index.
     */
    @Query(value = "{}", fields = "{ 'npi': 1, 'avgRating': 1, 'ratingCount': 1, '_id': 0 }", hint = "idx_npi_avgRating_ratingCount")
    List<DoctorProjection> findAllBy();

    /** * Checks if a doctor exists with the specified National Provider Identifier (NPI). */
    boolean existsByNpi(String npi);

    /** * Retrieves a doctor based on their unique National Provider Identifier (NPI). */
    Optional<Doctor> findByNpi(String npi);

    // 1. READS THE PROJECTIONS (Excluding _id for a cleaner DTO mapping)
    /** * Retrieves a list of doctors whose 'updated' flag is true, indicating pending synchronization.
     * Only fetches the fields necessary for syncing with other databases (e.g., Neo4j).
     */
    @Query(value = "{ 'updated': true }", fields = "{ 'npi': 1, 'telephone': 1, 'location': 1, '_id': 0 }")
    List<DoctorUpdateProjection> findAllPendingSyncs();

    // 2. RESETS THE FLAG USING THE NPI
    /** * Marks a doctor as synchronized by setting the 'updated' flag back to false.
     * The @Update annotation allows executing an atomic update operation without needing to fetch and save the entire document.
     */
    @Query("{ 'npi': ?0 }")
    @Update("{ '$set': { 'updated': false } }")
    void markAsSyncedByNpi(String npi);
}