package it.unipi.findyourdoc.service.exception;

/**
 * Custom runtime exception for business logic violations. Used when operations fail due to domain
 * rules rather than technical errors. Examples: duplicate entries, invalid state transitions,
 * constraint violations.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(Exception ex) {
        super(ex);
    }

    public BusinessException(String message, Exception ex) {
        super(message, ex);
    }
}