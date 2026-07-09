package io.github.huseyinbabal.taskhub.task.dto;

import java.time.Instant;
import java.time.LocalDate;

import io.github.huseyinbabal.taskhub.task.TaskPriority;
import io.github.huseyinbabal.taskhub.task.TaskStatus;

/** API view of a task (SPEC §4 boundary — never the entity). */
public record TaskResponse(
        Long id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        Long projectId,
        Long assigneeId,
        String assigneeUsername,
        Instant createdAt) {
}
