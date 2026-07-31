package io.github.huseyinbabal.taskhub.notification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Business metrics for task activity (SPEC §Session 8). Rides the same
 * {@link TaskChangedEvent} the gRPC publisher listens to, so counting costs the
 * service layer nothing and cannot be forgotten at a new call site.
 *
 * <p>Unlike {@link TaskEventPublisher} this listens <em>in</em> the transaction
 * rather than after commit: a rolled-back mutation is rare, and a metric that
 * silently drops events is worse than one that occasionally over-counts.
 */
@Component
public class TaskMetrics {

    /** Exposed as {@code taskhub_tasks_changed_total{type="..."}} on /actuator/prometheus. */
    public static final String TASKS_CHANGED = "taskhub.tasks.changed";

    private final MeterRegistry registry;

    public TaskMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @EventListener
    public void onTaskChanged(TaskChangedEvent event) {
        Counter.builder(TASKS_CHANGED)
                .description("Task mutations, split by what happened to the task")
                .tag("type", event.type().name())
                .register(this.registry)
                .increment();
    }
}
