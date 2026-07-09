package io.github.huseyinbabal.taskhub.common;

/**
 * Thrown when creating a resource would violate a uniqueness constraint (e.g. a
 * taken username/email or tag name). Mapped to a 409 problem-JSON response by
 * {@link GlobalExceptionHandler}.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
