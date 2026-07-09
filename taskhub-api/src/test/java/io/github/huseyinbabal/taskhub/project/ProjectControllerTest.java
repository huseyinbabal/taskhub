package io.github.huseyinbabal.taskhub.project;

import java.time.Instant;
import java.util.List;

import io.github.huseyinbabal.taskhub.common.AccessDeniedException;
import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.config.CorsConfig;
import io.github.huseyinbabal.taskhub.config.SecurityConfig;
import io.github.huseyinbabal.taskhub.project.dto.ProjectRequest;
import io.github.huseyinbabal.taskhub.project.dto.ProjectResponse;
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

/**
 * Slice test for project endpoints over the real security chain (SPEC §Session 2
 * acceptance #1–#4): unauthenticated → 401, foreign project → 403, invalid body
 * → 400, and list responses carry page metadata.
 */
@WebMvcTest(ProjectController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@TestPropertySource(properties = "taskhub.jwt.secret=" + TestTokens.DEV_SECRET)
class ProjectControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProjectService projectService;

    private ProjectResponse sampleResponse(Long id) {
        return new ProjectResponse(id, "Website", "Marketing site", 1L, "alice", Instant.parse("2026-07-09T10:00:00Z"));
    }

    @Test
    void create_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Website\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_authenticated_returns201WithLocation() throws Exception {
        when(projectService.create(any(ProjectRequest.class))).thenReturn(sampleResponse(10L));

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Website\",\"description\":\"Marketing site\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/projects/10"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.ownerUsername").value("alice"));
    }

    @Test
    void create_invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void get_foreignProject_returns403() throws Exception {
        when(projectService.get(10L)).thenThrow(new AccessDeniedException("no access"));

        mockMvc.perform(get("/api/projects/10")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("alice", "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_returnsPageWithMetadata() throws Exception {
        when(projectService.list(eq(0), eq(20)))
                .thenReturn(new PageResponse<>(List.of(sampleResponse(10L)), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/projects?page=0&size=20")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }
}
