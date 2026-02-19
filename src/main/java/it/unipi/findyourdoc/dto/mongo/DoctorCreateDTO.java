package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;

/**
 * DTO for medical professional onboarding.
 * Extends base user credentials with regulatory identifiers and professional metadata.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DTO for registering a new medical professional, including credentials and professional info")
public class DoctorCreateDTO extends UserCreateDTO {

    /** * National Provider Identifier: unique across all databases (Mongo/Neo4j). */
    @NotBlank(message = "NPI (National Provider Identifier) is required")
    @Schema(description = "Unique National Provider Identifier", example = "1234567890")
    private String npi;

    /** * Plaintext password to be hashed before persistence via BCrypt. */
    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Schema(description = "The raw password (plaintext) to be encrypted", example = "DoctorStrongPass123!")
    private String password;

    @NotBlank(message = "First name is required")
    @Schema(description = "The doctor's first name", example = "Giulia")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Schema(description = "The doctor's last name", example = "Bianchi")
    private String lastName;

    /** * Professional domains. Used for graph-based matching in Neo4j. */
    @NotEmpty(message = "Specialization is required")
    @Schema(description = "The medical areas of expertise", example = "[\"Cardiology\", \"Dermatology\"]")
    private ArrayList<String> specializations;

    @NotBlank(message = "Gender is required")
    @Schema(description = "The doctor's gender", example = "Female")
    private String gender;

    /** * Primary practice address used for geospatial indexing in MongoDB. */
    @NotNull(message = "Office location is required")
    @Schema(description = "The physical location of the doctor's office")
    private LocationDTO location;
}