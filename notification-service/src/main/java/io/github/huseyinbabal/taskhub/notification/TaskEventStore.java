package io.github.huseyinbabal.taskhub.notification;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import io.github.huseyinbabal.taskhub.notification.grpc.TaskEvent;
import org.springframework.stereotype.Component;

/**
 * Keeps the most recent task events the service was notified about, so an operator
 * (or a test) can see what arrived (SPEC §Session 3: the unary handler persists and
 * fans out).
 *
 * <p>Deliberately in-memory and bounded — the notification service owns no schema
 * yet; durable storage arrives with the database slice (Session 4). Nothing here
 * may be treated as a system of record.
 */
@Component
public class TaskEventStore {

    private static final int CAPACITY = 100;

    private final Deque<TaskEvent> events = new ArrayDeque<>(CAPACITY);

    /** Records {@code event}, evicting the oldest once at capacity. */
    public synchronized void record(TaskEvent event) {
        if (this.events.size() == CAPACITY) {
            this.events.removeFirst();
        }
        this.events.addLast(event);
    }

    /** The retained events, oldest first. */
    public synchronized List<TaskEvent> recent() {
        return List.copyOf(this.events);
    }
}
