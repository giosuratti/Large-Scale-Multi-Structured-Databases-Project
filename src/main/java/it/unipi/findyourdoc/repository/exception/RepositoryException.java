package it.unipi.findyourdoc.repository.exception;

/**
 * Custom runtime exception for data access layer failures in FindYourDoc.
 * Wraps underlying MongoDB or Spring Data exceptions.
 */
public class RepositoryException extends RuntimeException {

    public RepositoryException(Exception ex) {
        super(ex);
    }

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Exception ex) {
        super(message, ex);
    }
}
