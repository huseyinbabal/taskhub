package io.github.huseyinbabal.taskhub.notification;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.huseyinbabal.taskhub.notification.grpc.SubscribeRequest;
import io.github.huseyinbabal.taskhub.notification.grpc.TaskEvent;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory fan-out of task events to the streams opened by
 * {@code SubscribeTaskEvents} (SPEC §Session 3). Subscribers are held only for the
 * lifetime of their stream: cancelled and completed streams deregister themselves,
 * and a subscriber whose stream is not ready is skipped rather than blocking the
 * publisher.
 *
 * <p>Fan-out is process-local. Running more than one replica needs a shared bus
 * (Session 8 territory); until then a subscriber only sees events reported to the
 * replica it is connected to.
 */
@Component
public class TaskEventBroker {

    /** Subscribes to every project. */
    private static final long ALL_PROJECTS = 0L;

    private static final Logger log = LoggerFactory.getLogger(TaskEventBroker.class);

    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    /**
     * Registers {@code stream} to receive matching events until the caller cancels
     * it or the stream fails.
     */
    public void subscribe(SubscribeRequest request, StreamObserver<TaskEvent> stream) {
        Subscriber subscriber = new Subscriber(request.getProjectId(), stream);
        this.subscribers.add(subscriber);
        if (stream instanceof ServerCallStreamObserver<TaskEvent> serverStream) {
            serverStream.setOnCancelHandler(() -> remove(subscriber, "cancelled"));
            serverStream.setOnCloseHandler(() -> remove(subscriber, "closed"));
        }
        log.debug("Subscriber registered for project {} ({} live)", request.getProjectId(), this.subscribers.size());
    }

    /**
     * Pushes {@code event} to every subscriber watching its project.
     *
     * @return how many subscribers the event was delivered to
     */
    public int publish(TaskEvent event) {
        int delivered = 0;
        for (Subscriber subscriber : this.subscribers) {
            if (subscriber.watches(event.getProjectId()) && subscriber.send(event)) {
                delivered++;
            }
        }
        return delivered;
    }

    /** Number of live subscriber streams. */
    public int subscriberCount() {
        return this.subscribers.size();
    }

    private void remove(Subscriber subscriber, String reason) {
        if (this.subscribers.remove(subscriber)) {
            log.debug("Subscriber for project {} {} ({} live)", subscriber.projectId, reason, this.subscribers.size());
        }
    }

    private final class Subscriber {

        private final long projectId;

        private final StreamObserver<TaskEvent> stream;

        private Subscriber(long projectId, StreamObserver<TaskEvent> stream) {
            this.projectId = projectId;
            this.stream = stream;
        }

        private boolean watches(long eventProjectId) {
            return this.projectId == ALL_PROJECTS || this.projectId == eventProjectId;
        }

        /** Sends the event, dropping this subscriber if its stream has already gone away. */
        private boolean send(TaskEvent event) {
            try {
                this.stream.onNext(event);
                return true;
            }
            catch (RuntimeException ex) {
                log.warn("Dropping subscriber for project {}: {}", this.projectId, ex.toString());
                remove(this, "failed");
                return false;
            }
        }
    }
}
