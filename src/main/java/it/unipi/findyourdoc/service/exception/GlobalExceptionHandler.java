package it.unipi.findyourdoc.service.exception;

import java.util.HashMap;
import java.util.Map;
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
import it.unipi.findyourdoc.repository.exception.RepositoryException;
/**
 * Centralized exception handler for FindYourDoc API.
 * Converts internal exceptions into standard JSON responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Gestisce le eccezioni del Database (Repository).
     */
    @ExceptionHandler(RepositoryException.class)
    public ResponseEntity<Map<String, String>> handleRepositoryException(RepositoryException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Database Error");
        response.put("details", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Gestisce le ResponseStatusException (usate nei nostri Service per 404, 400, ecc.).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getStatusCode().toString());
        response.put("details", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }

    /**
     * Gestisce gli errori di validazione dei DTO (@Valid nelle RequestBody).
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
     * Gestisce errori di tipo nei parametri (es. mandare una stringa dove serve un numero).
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
     * Gestisce errori di sicurezza (Accesso negato/Ruolo mancante).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDeniedException(AccessDeniedException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Access Denied");
        response.put("details", "You don't have the required permissions for this operation.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Gestisce errori di autenticazione (Login fallito).
     */
    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ResponseEntity<Map<String, String>> handleAuthException(Exception ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Unauthorized");
        response.put("details", "Invalid email or password.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Fallback per qualsiasi altro errore imprevisto.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Internal Server Error");
        response.put("details", "An unexpected error occurred: " + ex.getMessage());
        // Loggare l'eccezione qui per debug interno
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}