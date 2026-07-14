package io.github.huseyinbabal.taskhub.task;

import java.util.Objects;

import io.github.huseyinbabal.taskhub.common.AccessDeniedException;
import io.github.huseyinbabal.taskhub.common.PageRequests;
import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.common.ResourceNotFoundException;
import io.github.huseyinbabal.taskhub.notification.TaskChangedEvent;
import io.github.huseyinbabal.taskhub.project.Project;
import io.github.huseyinbabal.taskhub.project.ProjectRepository;
import io.github.huseyinbabal.taskhub.security.CurrentUserProvider;
import io.github.huseyinbabal.taskhub.task.dto.TaskRequest;
import io.github.huseyinbabal.taskhub.task.dto.TaskResponse;
import io.github.huseyinbabal.taskhub.user.Role;
import io.github.huseyinbabal.taskhub.user.User;
import io.github.huseyinbabal.taskhub.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task CRUD under a project (SPEC §Session 2). Access follows the parent project's
 * ownership: a {@code USER} may only touch tasks in their own projects; an
 * {@code ADMIN} may touch any. An optional assignee must be an existing user.
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationEventPublisher events;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository,
                       UserRepository userRepository, TaskMapper taskMapper,
                       CurrentUserProvider currentUserProvider, ApplicationEventPublisher events) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.taskMapper = taskMapper;
        this.currentUserProvider = currentUserProvider;
        this.events = events;
    }

    @Transactional
    public TaskResponse create(Long projectId, TaskRequest request) {
        Project project = requireAccessibleProject(projectId);
        Task task = new Task(
                request.title(),
                request.description(),
                request.status(),
                request.priority(),
                request.dueDate(),
                project,
                resolveAssignee(request.assigneeId()));
        Task saved = taskRepository.save(task);
        publish(TaskChangedEvent.Type.CREATED, saved);
        return taskMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> listByProject(Long projectId, Integer page, Integer size) {
        requireAccessibleProject(projectId);
        PageRequest pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Task> tasks = taskRepository.findByProjectId(projectId, pageable);
        return PageResponse.from(tasks.map(taskMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        return taskMapper.toResponse(requireAccessibleTask(id));
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = requireAccessibleTask(id);
        TaskStatus previousStatus = task.getStatus();
        Long previousAssigneeId = (task.getAssignee() != null) ? task.getAssignee().getId() : null;

        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(request.status());
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        task.setAssignee(resolveAssignee(request.assigneeId()));
        Task saved = taskRepository.save(task);

        publish(changeType(saved, previousStatus, previousAssigneeId), saved);
        return taskMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Task task = requireAccessibleTask(id);
        taskRepository.delete(task);
        publish(TaskChangedEvent.Type.DELETED, task);
    }

    /**
     * Classifies an update by what actually changed, so subscribers can react to a task
     * being completed or reassigned without diffing snapshots themselves. Completion wins
     * over reassignment when an update does both.
     */
    private TaskChangedEvent.Type changeType(Task task, TaskStatus previousStatus, Long previousAssigneeId) {
        Long assigneeId = (task.getAssignee() != null) ? task.getAssignee().getId() : null;
        if (task.getStatus() == TaskStatus.DONE && previousStatus != TaskStatus.DONE) {
            return TaskChangedEvent.Type.COMPLETED;
        }
        if (!Objects.equals(assigneeId, previousAssigneeId)) {
            return TaskChangedEvent.Type.ASSIGNED;
        }
        return TaskChangedEvent.Type.UPDATED;
    }

    /**
     * Announces a task change. The event is only shipped to notification-service once
     * this transaction commits (see {@code TaskEventPublisher}), so a rolled-back mutation
     * never announces itself.
     */
    private void publish(TaskChangedEvent.Type type, Task task) {
        String actor = currentUserProvider.require().getUsername();
        events.publishEvent(TaskChangedEvent.of(type, task, actor));
    }

    private Task requireAccessibleTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task " + id + " not found"));
        authorize(task.getProject());
        return task;
    }

    private Project requireAccessibleProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + projectId + " not found"));
        authorize(project);
        return project;
    }

    /** A USER may only reach tasks within a project they own; an ADMIN reaches any. */
    private void authorize(Project project) {
        User user = currentUserProvider.require();
        if (!user.getRoles().contains(Role.ADMIN) && !project.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have access to project " + project.getId());
        }
    }

    private User resolveAssignee(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("User " + assigneeId + " not found"));
    }
}
