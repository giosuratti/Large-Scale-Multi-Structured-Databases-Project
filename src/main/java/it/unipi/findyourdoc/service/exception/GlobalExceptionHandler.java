package it.unipi.findyourdoc.service.exception;

import it.unipi.findyourdoc.repository.exception.RepositoryException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler for the FindYourDoc API.
 * Intercepts internal exceptions and converts them into standardized, client-friendly JSON responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Intercepts database and persistence layer failures.
     * Prevents internal query structures from leaking to the frontend.
     */
    @ExceptionHandler(RepositoryException.class)
    public ResponseEntity<Map<String, String>> handleRepositoryException(RepositoryException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Database Error");
        response.put("details", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Handles standard HTTP status exceptions explicitly thrown by the business logic (e.g., 404 Not Found).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getStatusCode().toString());
        response.put("details", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }

    /**
     * Processes DTO validation failures triggered by @Valid annotations.
     * Maps specific field errors to a structured dictionary for the client UI.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleDtoValidationException(MethodArgumentNotValidException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Validation Error");

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        response.put("details", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Catches type mismatches in request parameters or path variables.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Invalid Parameter Type");
        response.put("details", String.format("Parameter '%s' expects type '%s'",
                ex.getName(), ex.getRequiredType().getSimpleName()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Intercepts authorization failures when an authenticated user attempts an action outside their role scope.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDeniedException(AccessDeniedException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Access Denied");
        response.put("details", "You don't have the required permissions for this operation: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handles authentication failures, such as invalid email/password combinations during login.
     */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<Map<String, String>> handleAuthException(Exception ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Unauthorized");
        response.put("details", "Invalid email or password.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Fallback handler for any uncaught runtime exceptions to ensure the API never returns a raw HTML error page.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Internal Server Error");
        response.put("details", "An unexpected error occurred.");
        // Note: The raw exception message is hidden from the client in production for security.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}