package it.unipi.findyourdoc.service;

import it.unipi.findyourdoc.dto.mongo.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for administrative operations.
 * Defines methods for managing administrators, users, and system-wide synchronization tasks.
 */
public interface AdminService {

    /** * Creates a new administrator account. */
    AdminReadDTO createAdmin(AdminCreateDTO createDTO);

    /** * Searches for administrators using an email prefix. */
    List<AdminReadDTO> searchAdmins(String emailPrefix);

    /** * Retrieves a paginated list of all administrators. */
    Page<AdminReadDTO> getAllAdmins(Pageable pageable);

    /** * Updates an existing administrator's details based on their email. */
    AdminReadDTO updateAdmin(String email, AdminUpdateDTO updateDTO);

    /** * Retrieves an administrator by their exact email address. */
    AdminReadDTO getAdminByEmail(String email);

    /** * Deletes any user (Admin, Doctor, or Patient) by their email. */
    void deleteUser(String email);

    /** * Forcefully changes a user's password using their unique database ID. */
    void changeUserPassword(String id, String password);

    /** * Synchronizes doctor ratings to ensure consistency across the system. */
    void syncDoctorRatings();

    /** * Registers a new doctor into the system. */
    DoctorReadDTO registerDoctor(DoctorCreateDTO createDTO);

    /** * Prepares and syncs a single doctor's updated data for Neo4j batch processing. */
    //void performSingleDoctorSync(Doctor doctor, List<DoctorGraphUpdateProjection> neo4jBatch);

    /** * Triggers a full synchronization of all doctors between MongoDB and Neo4j. */
    void syncAllDoctors();
}