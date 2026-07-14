package io.github.huseyinbabal.taskhub.notification;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.protobuf.Timestamp;
import io.github.huseyinbabal.taskhub.notification.grpc.NotificationServiceGrpc;
import io.github.huseyinbabal.taskhub.notification.grpc.NotifyAck;
import io.github.huseyinbabal.taskhub.notification.grpc.TaskEvent;
import io.github.huseyinbabal.taskhub.notification.grpc.TaskEventType;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ships task mutations to notification-service over gRPC (SPEC §Session 3:
 * taskhub-api publishes task events as a gRPC client).
 *
 * <p>Two properties matter here. It fires <em>after commit</em>, so a rolled-back
 * mutation never announces itself. And notifications are best-effort: a
 * notification-service that is slow, down, or rejecting the call must not fail a
 * task mutation that has already been committed, so gRPC failures are logged and
 * swallowed rather than propagated. Every call is bounded by a deadline so the
 * request thread cannot hang on an unresponsive server.
 */
@Component
public class TaskEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TaskEventPublisher.class);

    private static final Duration DEADLINE = Duration.ofSeconds(2);

    /** Proto uses 0 for "no assignee": proto3 scalars have no null. */
    private static final long UNASSIGNED = 0L;

    private final NotificationServiceGrpc.NotificationServiceBlockingStub notifications;

    public TaskEventPublisher(NotificationServiceGrpc.NotificationServiceBlockingStub notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskChanged(TaskChangedEvent event) {
        TaskEvent request = toProto(event);
        try {
            NotifyAck ack = this.notifications
                    .withDeadlineAfter(DEADLINE.toMillis(), TimeUnit.MILLISECONDS)
                    .notifyTaskEvent(request);
            log.debug("Task event {} acknowledged (delivered to {} subscriber(s))",
                    ack.getEventId(), ack.getDeliveredTo());
        }
        catch (StatusRuntimeException ex) {
            // The task change is already committed; notification is best-effort.
            log.warn("Could not notify task event {} for task {}: {}",
                    request.getEventId(), event.taskId(), ex.getStatus());
        }
    }

    private TaskEvent toProto(TaskChangedEvent event) {
        return TaskEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setType(toProtoType(event.type()))
                .setTaskId(event.taskId())
                .setProjectId(event.projectId())
                .setTitle(event.title())
                .setStatus(event.status().name())
                .setAssigneeId((event.assigneeId() != null) ? event.assigneeId() : UNASSIGNED)
                .setActor(event.actor())
                .setOccurredAt(Timestamp.newBuilder()
                        .setSeconds(event.occurredAt().getEpochSecond())
                        .setNanos(event.occurredAt().getNano())
                        .build())
                .build();
    }

    private TaskEventType toProtoType(TaskChangedEvent.Type type) {
        return switch (type) {
            case CREATED -> TaskEventType.TASK_CREATED;
            case UPDATED -> TaskEventType.TASK_UPDATED;
            case ASSIGNED -> TaskEventType.TASK_ASSIGNED;
            case COMPLETED -> TaskEventType.TASK_COMPLETED;
            case DELETED -> TaskEventType.TASK_DELETED;
        };
    }
}
