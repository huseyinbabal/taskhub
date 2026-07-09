package io.github.huseyinbabal.taskhub.security;

import io.github.huseyinbabal.taskhub.common.ResourceNotFoundException;
import io.github.huseyinbabal.taskhub.user.User;
import io.github.huseyinbabal.taskhub.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@link User} behind the current authenticated request from the
 * security context. Services use it to enforce ownership (a {@code USER} may only
 * touch their own resources) without threading the principal through every call.
 */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** The authenticated user, or throws if there is no valid principal in context. */
    public User require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in context");
        }
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found: " + username));
    }
}
