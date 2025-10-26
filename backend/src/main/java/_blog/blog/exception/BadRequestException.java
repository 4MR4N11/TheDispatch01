package _blog.blog.exception;

/**
 * Exception thrown when request is invalid or malformed
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
