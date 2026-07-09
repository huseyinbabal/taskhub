package io.github.huseyinbabal.taskhub.task;

import io.github.huseyinbabal.taskhub.task.dto.TaskResponse;
import io.github.huseyinbabal.taskhub.user.User;
import org.springframework.stereotype.Component;

/**
 * Hand-written task ↔ DTO mapping (consistent with the project's mapping
 * approach). Invoke within the owning transaction so lazy {@code project}/
 * {@code assignee} associations resolve.
 */
@Component
public class TaskMapper {

    public TaskResponse toResponse(Task task) {
        User assignee = task.getAssignee();
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getProject().getId(),
                assignee == null ? null : assignee.getId(),
                assignee == null ? null : assignee.getUsername(),
                task.getCreatedAt());
    }
}
