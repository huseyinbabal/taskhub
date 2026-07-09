package io.github.huseyinbabal.taskhub.task;

import java.time.Instant;

import io.github.huseyinbabal.taskhub.common.AccessDeniedException;
import io.github.huseyinbabal.taskhub.config.CorsConfig;
import io.github.huseyinbabal.taskhub.config.SecurityConfig;
import io.github.huseyinbabal.taskhub.task.dto.TaskRequest;
import io.github.huseyinbabal.taskhub.task.dto.TaskResponse;
import io.github.huseyinbabal.taskhub.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test for task endpoints over the real security chain (SPEC §Session 2). */
@WebMvcTest(TaskController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@TestPropertySource(properties = "taskhub.jwt.secret=" + TestTokens.DEV_SECRET)
class TaskControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TaskService taskService;

    private TaskResponse sample(Long id) {
        return new TaskResponse(id, "Do it", "desc", TaskStatus.TODO, TaskPriority.HIGH,
                null, 10L, null, null, Instant.parse("2026-07-09T10:00:00Z"));
    }

    @Test
    void getTask_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/tasks/5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createTask_authenticated_returns201WithLocation() throws Exception {
        when(taskService.create(eq(10L), any(TaskRequest.class))).thenReturn(sample(5L));

        mockMvc.perform(post("/api/projects/10/tasks")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Do it\",\"status\":\"TODO\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/tasks/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.projectId").value(10));
    }

    @Test
    void createTask_invalidBody_returns400() throws Exception {
        // Missing required title/status/priority.
        mockMvc.perform(post("/api/projects/10/tasks")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"no title\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    void getTask_inForeignProject_returns403() throws Exception {
        when(taskService.get(5L)).thenThrow(new AccessDeniedException("no access"));

        mockMvc.perform(get("/api/tasks/5")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("alice", "USER")))
                .andExpect(status().isForbidden());
    }
}
