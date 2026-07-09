package io.github.huseyinbabal.taskhub.user.dto;

import java.util.List;

/**
 * Successful authentication result: a signed JWT plus the identity it encodes.
 *
 * @param token     the signed JWT to send as {@code Authorization: Bearer <token>}
 * @param tokenType always {@code Bearer}
 * @param username  the authenticated username
 * @param roles     the user's roles
 */
public record AuthResponse(String token, String tokenType, String username, List<String> roles) {

    public static AuthResponse bearer(String token, String username, List<String> roles) {
        return new AuthResponse(token, "Bearer", username, roles);
    }
}
