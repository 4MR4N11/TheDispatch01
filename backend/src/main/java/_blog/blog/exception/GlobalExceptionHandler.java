package _blog.blog.exception;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * ✅ SECURITY FIX: Global Exception Handler
 *
 * Prevents information disclosure by:
 * - Catching all exceptions before they reach the client
 * - Logging full stack traces server-side only
 * - Returning generic error messages to clients
 * - Preventing database schema/internal path exposure
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle generic runtime exceptions
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        // Log full details server-side
        log.error("Runtime exception occurred", ex);

        // Return generic message to client (prevents information disclosure)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(createErrorResponse(
                "An error occurred processing your request",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            ));
    }

    /**
     * Handle authentication failures
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());

        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(createErrorResponse(
                "Invalid username or password",
                HttpStatus.UNAUTHORIZED.value()
            ));
    }

    /**
     * Handle user not found
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UsernameNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());

        // Return generic message (prevents user enumeration)
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(createErrorResponse(
                "Invalid username or password",
                HttpStatus.UNAUTHORIZED.value()
            ));
    }

    /**
     * Handle resource not found exceptions
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(createErrorResponse(
                ex.getMessage(),  // Safe to expose - doesn't reveal internal details
                HttpStatus.NOT_FOUND.value()
            ));
    }

    /**
     * Handle validation errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Validation failed: {}", errors);

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of(
                "error", "Validation failed",
                "code", HttpStatus.BAD_REQUEST.value(),
                "details", errors
            ));
    }

    /**
     * Handle type mismatch (e.g., string instead of number for ID)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch: {} for parameter {}", ex.getValue(), ex.getName());

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(createErrorResponse(
                "Invalid parameter value",
                HttpStatus.BAD_REQUEST.value()
            ));
    }

    /**
     * Handle illegal arguments
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(createErrorResponse(
                "Invalid request parameters",
                HttpStatus.BAD_REQUEST.value()
            ));
    }

    /**
     * Handle null pointer exceptions
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointer(NullPointerException ex) {
        log.error("Null pointer exception occurred", ex);

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(createErrorResponse(
                "An error occurred processing your request",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            ));
    }

    /**
     * Catch-all for any other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected exception occurred", ex);

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(createErrorResponse(
                "An unexpected error occurred",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            ));
    }

    /**
     * Helper method to create consistent error responses
     */
    private Map<String, Object> createErrorResponse(String message, int code) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", message);
        response.put("code", code);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    @ExceptionHandler(BannedException.class)
    public ResponseEntity<Map<String, Object>> handleBannedException(BannedException ex) {
        log.warn("Banned user attempt: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(createErrorResponse(
                "Your account has been banned. Please contact support for more information.",
                HttpStatus.FORBIDDEN.value()
            ));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(createErrorResponse(
                ex.getMessage(),
                HttpStatus.BAD_REQUEST.value()
            ));
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(createErrorResponse(
                ex.getMessage(),
                HttpStatus.CONFLICT.value()
            ));
    }
}
