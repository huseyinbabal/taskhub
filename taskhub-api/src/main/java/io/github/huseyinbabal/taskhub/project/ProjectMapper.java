package io.github.huseyinbabal.taskhub.project;

import io.github.huseyinbabal.taskhub.project.dto.ProjectResponse;
import org.springframework.stereotype.Component;

/**
 * Hand-written entity ↔ DTO mapping (the mapping approach chosen for the project
 * per SPEC §5; kept consistent across features). Must be invoked within the
 * owning transaction so the lazy {@code owner} association resolves.
 */
@Component
public class ProjectMapper {

    public ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getOwner().getId(),
                project.getOwner().getUsername(),
                project.getCreatedAt());
    }
}
