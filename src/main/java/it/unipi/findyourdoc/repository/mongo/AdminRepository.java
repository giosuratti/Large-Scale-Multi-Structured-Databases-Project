package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.Admin;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Admin entities in MongoDB.
 * Extends {@link UserRepository} to inherit common user persistence operations.
 */
@Repository
public interface AdminRepository extends UserRepository<Admin>{

    /** * Checks if an administrator exists with the given email.
     * Utilized by the JwtTokenProvider for credential validation.
     */
    boolean existsByEmail(String email);

    /** * Retrieves an administrator based on their unique email address.
     */
    Optional<Admin> findByEmail(String email);

    /** * Finds a list of administrators whose email starts with a specific prefix.
     * Useful for administrative search or autocomplete features.
     */
    List<Admin> findByEmailStartingWith(String prefix);

}