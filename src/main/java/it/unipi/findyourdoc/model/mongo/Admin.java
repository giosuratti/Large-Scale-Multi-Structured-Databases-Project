package it.unipi.findyourdoc.model.mongo;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Entity representing an Administrator within the MongoDB "admins" collection.
 * Inherits common authentication and profile fields from the {@link User} class.
 */
@EqualsAndHashCode(callSuper = false)
@Data
@Document(collection = "admins")
public class Admin extends User {
    // This class inherits all properties from User and maps specifically to the admins collection
}