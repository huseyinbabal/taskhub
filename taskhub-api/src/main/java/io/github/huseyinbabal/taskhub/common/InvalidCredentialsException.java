package io.github.huseyinbabal.taskhub.common;

/**
 * Thrown when login credentials do not match a known user. Mapped to a 401
 * problem-JSON response by {@link GlobalExceptionHandler}. The message is kept
 * deliberately generic so it never reveals whether the username exists.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
