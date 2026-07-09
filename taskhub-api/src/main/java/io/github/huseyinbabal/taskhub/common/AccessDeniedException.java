package io.github.huseyinbabal.taskhub.common;

/**
 * Thrown when an authenticated user tries to act on a resource they do not own.
 * Mapped to a 403 problem-JSON response by {@link GlobalExceptionHandler}.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }
}
