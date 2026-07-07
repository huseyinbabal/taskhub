package io.github.huseyinbabal.taskhub.common;

/**
 * Thrown when a requested resource (Project, Task, User, Tag …) does not exist.
 * Mapped to a 404 problem-JSON response by {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
