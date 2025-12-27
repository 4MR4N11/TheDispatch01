package _blog.blog.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " with ID " + id + " not found");
    }

    public ResourceNotFoundException(String resourceName, String identifier) {
        super(resourceName + " '" + identifier + "' not found");
    }
}
