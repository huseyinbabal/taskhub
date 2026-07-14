package io.github.huseyinbabal.taskhub.notification;

import java.time.Instant;

import io.github.huseyinbabal.taskhub.task.Task;
import io.github.huseyinbabal.taskhub.task.TaskStatus;

/**
 * A task mutation worth telling the world about (SPEC §Session 3). Published by
 * {@code TaskService} inside the transaction and shipped over gRPC only once that
 * transaction commits — see {@link TaskEventPublisher}.
 *
 * <p>Carries a snapshot rather than the {@link Task} entity: by the time the event
 * is sent the persistence context is gone, and a deleted task no longer exists at
 * all.
 *
 * @param type       what happened to the task
 * @param taskId     the task the event is about
 * @param projectId  the project it belongs to — subscribers filter on this
 * @param title      task title at the time of the change
 * @param status     task status at the time of the change
 * @param assigneeId assignee at the time of the change, or {@code null} if unassigned
 * @param actor      username of the caller that made the change
 * @param occurredAt when the change happened
 */
public record TaskChangedEvent(
        Type type,
        long taskId,
        long projectId,
        String title,
        TaskStatus status,
        Long assigneeId,
        String actor,
        Instant occurredAt) {

    public enum Type {
        CREATED,
        UPDATED,
        ASSIGNED,
        COMPLETED,
        DELETED
    }

    /** Snapshots {@code task} as an event of the given type, attributed to {@code actor}. */
    public static TaskChangedEvent of(Type type, Task task, String actor) {
        return new TaskChangedEvent(
                type,
                task.getId(),
                task.getProject().getId(),
                task.getTitle(),
                task.getStatus(),
                (task.getAssignee() != null) ? task.getAssignee().getId() : null,
                actor,
                Instant.now());
    }
}
