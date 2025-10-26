package _blog.blog.exception;

/**
 * Exception thrown when trying to create a resource that already exists
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    public DuplicateResourceException(String resourceName, String identifier) {
        super(String.format("%s with identifier '%s' already exists", resourceName, identifier));
    }
}
