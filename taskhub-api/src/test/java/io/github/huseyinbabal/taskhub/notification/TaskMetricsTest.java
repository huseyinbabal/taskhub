package io.github.huseyinbabal.taskhub.notification;

import java.time.Instant;

import io.github.huseyinbabal.taskhub.task.TaskStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The business metric behind the Grafana RED dashboard and the task-throughput
 * alert (SPEC §Session 8): every task mutation is counted, split by what
 * happened to it.
 */
class TaskMetricsTest {

    private MeterRegistry registry;

    private TaskMetrics metrics;

    @BeforeEach
    void setUp() {
        this.registry = new SimpleMeterRegistry();
        this.metrics = new TaskMetrics(this.registry);
    }

    @Test
    void countsATaskChangeUnderItsOwnType() {
        this.metrics.onTaskChanged(event(TaskChangedEvent.Type.CREATED));

        assertThat(counter("CREATED")).isEqualTo(1.0);
    }

    @Test
    void keepsEachChangeTypeOnItsOwnCounter() {
        this.metrics.onTaskChanged(event(TaskChangedEvent.Type.CREATED));
        this.metrics.onTaskChanged(event(TaskChangedEvent.Type.CREATED));
        this.metrics.onTaskChanged(event(TaskChangedEvent.Type.COMPLETED));

        assertThat(counter("CREATED")).isEqualTo(2.0);
        assertThat(counter("COMPLETED")).isEqualTo(1.0);
        assertThat(counter("DELETED")).isZero();
    }

    private double counter(String type) {
        io.micrometer.core.instrument.Counter counter = this.registry
                .find(TaskMetrics.TASKS_CHANGED)
                .tag("type", type)
                .counter();
        return (counter != null) ? counter.count() : 0.0;
    }

    private TaskChangedEvent event(TaskChangedEvent.Type type) {
        return new TaskChangedEvent(type, 1L, 42L, "Write the dashboard",
                TaskStatus.TODO, null, "alice", Instant.now());
    }
}
