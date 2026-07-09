package io.github.huseyinbabal.taskhub.task;

import java.net.URI;

import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.task.dto.TaskRequest;
import io.github.huseyinbabal.taskhub.task.dto.TaskResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Task endpoints (SPEC §Session 2): tasks are created and listed under a project,
 * then addressed directly by id. All routes require authentication; access is
 * enforced against the parent project's owner in the service.
 */
@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/api/projects/{projectId}/tasks")
    public PageResponse<TaskResponse> listByProject(@PathVariable Long projectId,
                                                    @RequestParam(required = false) Integer page,
                                                    @RequestParam(required = false) Integer size) {
        return taskService.listByProject(projectId, page, size);
    }

    @PostMapping("/api/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> create(@PathVariable Long projectId,
                                               @Valid @RequestBody TaskRequest request) {
        TaskResponse created = taskService.create(projectId, request);
        return ResponseEntity.created(URI.create("/api/tasks/" + created.id())).body(created);
    }

    @GetMapping("/api/tasks/{id}")
    public TaskResponse get(@PathVariable Long id) {
        return taskService.get(id);
    }

    @PutMapping("/api/tasks/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return taskService.update(id, request);
    }

    @DeleteMapping("/api/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
