package it.unipi.findyourdoc.repository.exception;

/**
 * Custom runtime exception for data access layer failures in FindYourDoc.
 * Standardizes error handling by wrapping underlying MongoDB, Neo4j, or Spring Data exceptions.
 */
public class RepositoryException extends RuntimeException {

    /** * Wraps an existing exception to preserve the original stack trace. */
    public RepositoryException(Exception ex) {
        super(ex);
    }

    /** * Creates a new exception with a specific descriptive message. */
    public RepositoryException(String message) {
        super(message);
    }

    /** * Creates a new exception with both a custom message and the original cause. */
    public RepositoryException(String message, Exception ex) {
        super(message, ex);
    }
}