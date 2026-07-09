package io.github.huseyinbabal.taskhub.project.dto;

import java.time.Instant;

/** API view of a project — never the entity (SPEC §4 boundary). */
public record ProjectResponse(
        Long id,
        String name,
        String description,
        Long ownerId,
        String ownerUsername,
        Instant createdAt) {
}
