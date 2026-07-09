package io.github.huseyinbabal.taskhub.user;

import java.util.List;

import io.github.huseyinbabal.taskhub.user.dto.UserResponse;
import org.springframework.stereotype.Component;

/** Hand-written user ↔ DTO mapping (consistent with the project's approach). Never exposes the password hash. */
@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::name)
                .sorted()
                .toList();
        return new UserResponse(user.getId(), user.getEmail(), user.getUsername(), roles, user.getCreatedAt());
    }
}
