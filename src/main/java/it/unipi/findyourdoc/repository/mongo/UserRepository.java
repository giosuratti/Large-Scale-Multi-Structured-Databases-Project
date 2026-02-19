package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

/**
 * base repository interface for all user-based entities (Admin, Doctor, Patient).
 * It provides common persistence operations for objects extending the {@link User} class.
 * * @param <T> The specific user type this repository handles.
 */
@NoRepositoryBean // Prevents Spring from creating an instance of this intermediate repository
public interface UserRepository<T extends User> extends MongoRepository<T, String> {

    /** * Checks if a user already exists with the provided email address. */
    boolean existsByEmail(String email);

    /** * Removes a user record from the database based on their email. */
    void deleteByEmail(String email);

    /** * Retrieves a user by their unique email address. */
    Optional<T> findByEmail(String email);
}