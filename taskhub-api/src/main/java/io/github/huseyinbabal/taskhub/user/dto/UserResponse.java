package io.github.huseyinbabal.taskhub.user.dto;

import java.time.Instant;
import java.util.List;

/** API view of a user for administration (SPEC §Session 2 — ADMIN only). No password. */
public record UserResponse(
        Long id,
        String email,
        String username,
        List<String> roles,
        Instant createdAt) {
}
