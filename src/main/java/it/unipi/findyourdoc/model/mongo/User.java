package it.unipi.findyourdoc.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.FieldType;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.LocalDateTime;

/**
 * Base abstract class for all user types in the system (Admin, Doctor, Patient).
 * Contains common authentication credentials and account metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class User {

    /** * Unique identifier for the user, mapped as a MongoDB ObjectId. */
    @MongoId(FieldType.OBJECT_ID)
    private String id;

    /** * Primary login credential, unique across the entire database. */
    @Indexed(unique = true)
    private String email;

    private String telephone;

    /** * Hashed password string for secure authentication. */
    private String password;

    /** * Automatically assigned timestamp indicating when the user account was created. */
    @CreatedDate
    private LocalDateTime createdAt;
}