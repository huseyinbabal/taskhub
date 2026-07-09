package io.github.huseyinbabal.taskhub.task.dto;

import java.time.LocalDate;

import io.github.huseyinbabal.taskhub.task.TaskPriority;
import io.github.huseyinbabal.taskhub.task.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a task (SPEC §Session 2). The project is taken from
 * the path; {@code assigneeId} is optional.
 */
public record TaskRequest(

        @NotBlank @Size(max = 200)
        String title,

        @Size(max = 4000)
        String description,

        @NotNull
        TaskStatus status,

        @NotNull
        TaskPriority priority,

        LocalDate dueDate,

        Long assigneeId) {
}
