package io.github.huseyinbabal.taskhub.notification.grpc;

import io.grpc.MethodDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the shared gRPC contract (SPEC §Session 3): the stubs both modules
 * consume must expose a unary {@code NotifyTaskEvent} and a server-streaming
 * {@code SubscribeTaskEvents}. A change to either RPC's name or streaming
 * mode is a breaking wire change and fails here.
 */
class NotificationContractTest {

    @Test
    void notifyTaskEventIsUnary() {
        MethodDescriptor<TaskEvent, NotifyAck> method = NotificationServiceGrpc.getNotifyTaskEventMethod();

        assertThat(method.getType()).isEqualTo(MethodDescriptor.MethodType.UNARY);
        assertThat(method.getFullMethodName()).isEqualTo("taskhub.notification.v1.NotificationService/NotifyTaskEvent");
    }

    @Test
    void subscribeTaskEventsIsServerStreaming() {
        MethodDescriptor<SubscribeRequest, TaskEvent> method = NotificationServiceGrpc.getSubscribeTaskEventsMethod();

        assertThat(method.getType()).isEqualTo(MethodDescriptor.MethodType.SERVER_STREAMING);
        assertThat(method.getFullMethodName())
                .isEqualTo("taskhub.notification.v1.NotificationService/SubscribeTaskEvents");
    }

    @Test
    void taskEventCarriesTheFieldsSubscribersNeed() throws Exception {
        TaskEvent event = TaskEvent.newBuilder()
                .setEventId("evt-1")
                .setType(TaskEventType.TASK_ASSIGNED)
                .setTaskId(7L)
                .setProjectId(3L)
                .setTitle("Ship session 3")
                .setStatus("IN_PROGRESS")
                .setAssigneeId(42L)
                .setActor("alice")
                .build();

        assertThat(event.getType()).isEqualTo(TaskEventType.TASK_ASSIGNED);
        assertThat(event.getTaskId()).isEqualTo(7L);
        assertThat(event.getAssigneeId()).isEqualTo(42L);
        assertThat(TaskEvent.parseFrom(event.toByteArray())).isEqualTo(event);
    }
}
