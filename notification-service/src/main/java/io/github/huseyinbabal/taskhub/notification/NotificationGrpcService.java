package io.github.huseyinbabal.taskhub.notification;

import io.github.huseyinbabal.taskhub.notification.grpc.NotificationServiceGrpc;
import io.github.huseyinbabal.taskhub.notification.grpc.NotifyAck;
import io.github.huseyinbabal.taskhub.notification.grpc.SubscribeRequest;
import io.github.huseyinbabal.taskhub.notification.grpc.TaskEvent;
import io.github.huseyinbabal.taskhub.notification.grpc.interceptor.GrpcMetadata;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;

/**
 * The gRPC edge of the notification service (SPEC §Session 3).
 *
 * <p>{@code NotifyTaskEvent} is the write side: taskhub-api reports a task mutation,
 * the event is recorded and fanned out, and the caller gets an acknowledgement
 * carrying the number of subscribers reached. {@code SubscribeTaskEvents} is the read
 * side: the stream stays open and receives events as they arrive, so it is never
 * completed here — the client ends it by cancelling.
 */
@GrpcService
public class NotificationGrpcService extends NotificationServiceGrpc.NotificationServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(NotificationGrpcService.class);

    private final TaskEventStore store;

    private final TaskEventBroker broker;

    public NotificationGrpcService(TaskEventStore store, TaskEventBroker broker) {
        this.store = store;
        this.broker = broker;
    }

    @Override
    public void notifyTaskEvent(TaskEvent request, StreamObserver<NotifyAck> responseObserver) {
        this.store.record(request);
        int delivered = this.broker.publish(request);
        log.info("Task event {} ({}) for task {} from {} delivered to {} subscriber(s)",
                request.getEventId(), request.getType(), request.getTaskId(), GrpcMetadata.caller(), delivered);

        responseObserver.onNext(NotifyAck.newBuilder()
                .setEventId(request.getEventId())
                .setAccepted(true)
                .setDeliveredTo(delivered)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void subscribeTaskEvents(SubscribeRequest request, StreamObserver<TaskEvent> responseObserver) {
        this.broker.subscribe(request, responseObserver);
    }
}
