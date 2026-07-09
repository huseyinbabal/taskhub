package io.github.huseyinbabal.taskhub.task;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.huseyinbabal.taskhub.common.AccessDeniedException;
import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.common.ResourceNotFoundException;
import io.github.huseyinbabal.taskhub.project.Project;
import io.github.huseyinbabal.taskhub.project.ProjectRepository;
import io.github.huseyinbabal.taskhub.security.CurrentUserProvider;
import io.github.huseyinbabal.taskhub.task.dto.TaskRequest;
import io.github.huseyinbabal.taskhub.task.dto.TaskResponse;
import io.github.huseyinbabal.taskhub.user.Role;
import io.github.huseyinbabal.taskhub.user.User;
import io.github.huseyinbabal.taskhub.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for task CRUD + access (SPEC §Session 2). Task access follows the
 * parent project's ownership; assignees must exist.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    TaskRepository taskRepository;
    @Mock
    ProjectRepository projectRepository;
    @Mock
    UserRepository userRepository;
    @Mock
    CurrentUserProvider currentUserProvider;

    TaskService taskService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, projectRepository, userRepository,
                new TaskMapper(), currentUserProvider);
        alice = userWithId(1L, "alice", Role.USER);
        bob = userWithId(2L, "bob", Role.USER);
    }

    private User userWithId(Long id, String username, Role role) {
        User user = new User(username + "@example.com", username, "hash", Set.of(role));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Project projectOwnedBy(Long id, User owner) {
        Project project = new Project("Website", null, owner);
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    private Task taskIn(Long id, Project project) {
        Task task = new Task("Do it", null, TaskStatus.TODO, TaskPriority.LOW, null, project, null);
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    private TaskRequest request() {
        return new TaskRequest("Do it", "desc", TaskStatus.TODO, TaskPriority.HIGH, null, null);
    }

    @Test
    void create_inOwnProject_persistsTask() {
        when(currentUserProvider.require()).thenReturn(alice);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(projectOwnedBy(10L, alice)));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskResponse response = taskService.create(10L, request());

        assertThat(response.title()).isEqualTo("Do it");
        assertThat(response.projectId()).isEqualTo(10L);
        assertThat(response.priority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    void create_inForeignProject_throwsAccessDenied() {
        when(currentUserProvider.require()).thenReturn(alice);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(projectOwnedBy(10L, bob)));

        assertThatThrownBy(() -> taskService.create(10L, request()))
                .isInstanceOf(AccessDeniedException.class);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void create_inMissingProject_throwsNotFound() {
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.create(404L, request()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withUnknownAssignee_throwsNotFound() {
        when(currentUserProvider.require()).thenReturn(alice);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(projectOwnedBy(10L, alice)));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        TaskRequest withAssignee = new TaskRequest("Do it", null, TaskStatus.TODO, TaskPriority.LOW, null, 99L);
        assertThatThrownBy(() -> taskService.create(10L, withAssignee))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_taskInOwnProject_returnsIt() {
        when(currentUserProvider.require()).thenReturn(alice);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(taskIn(5L, projectOwnedBy(10L, alice))));

        assertThat(taskService.get(5L).id()).isEqualTo(5L);
    }

    @Test
    void get_taskInForeignProject_throwsAccessDenied() {
        when(currentUserProvider.require()).thenReturn(alice);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(taskIn(5L, projectOwnedBy(10L, bob))));

        assertThatThrownBy(() -> taskService.get(5L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void get_missingTask_throwsNotFound() {
        when(taskRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.get(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_ownTask_appliesChanges() {
        when(currentUserProvider.require()).thenReturn(alice);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(taskIn(5L, projectOwnedBy(10L, alice))));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskResponse response = taskService.update(5L,
                new TaskRequest("Renamed", null, TaskStatus.DONE, TaskPriority.MEDIUM, null, null));

        assertThat(response.title()).isEqualTo("Renamed");
        assertThat(response.status()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    void delete_ownTask_deletes() {
        Task task = taskIn(5L, projectOwnedBy(10L, alice));
        when(currentUserProvider.require()).thenReturn(alice);
        when(taskRepository.findById(5L)).thenReturn(Optional.of(task));

        taskService.delete(5L);

        verify(taskRepository).delete(task);
    }

    @Test
    void listByProject_ownProject_returnsPage() {
        when(currentUserProvider.require()).thenReturn(alice);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(projectOwnedBy(10L, alice)));
        when(taskRepository.findByProjectId(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(taskIn(5L, projectOwnedBy(10L, alice)))));

        PageResponse<TaskResponse> response = taskService.listByProject(10L, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void listByProject_foreignProject_throwsAccessDenied() {
        when(currentUserProvider.require()).thenReturn(alice);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(projectOwnedBy(10L, bob)));

        assertThatThrownBy(() -> taskService.listByProject(10L, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
        verify(taskRepository, never()).findByProjectId(any(), any());
    }
}
