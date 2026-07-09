package io.github.huseyinbabal.taskhub.tag;

import java.util.List;

import io.github.huseyinbabal.taskhub.common.DuplicateResourceException;
import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.config.CorsConfig;
import io.github.huseyinbabal.taskhub.config.SecurityConfig;
import io.github.huseyinbabal.taskhub.tag.dto.TagRequest;
import io.github.huseyinbabal.taskhub.tag.dto.TagResponse;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice test for tag endpoints over the real security chain (SPEC §Session 2). */
@WebMvcTest(TagController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@TestPropertySource(properties = "taskhub.jwt.secret=" + TestTokens.DEV_SECRET)
class TagControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TagService tagService;

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_authenticated_returns201() throws Exception {
        when(tagService.create(any(TagRequest.class))).thenReturn(new TagResponse(5L, "urgent", "#ff0000"));

        mockMvc.perform(post("/api/tags")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"urgent\",\"color\":\"#ff0000\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("urgent"));
    }

    @Test
    void create_duplicate_returns409() throws Exception {
        when(tagService.create(any(TagRequest.class)))
                .thenThrow(new DuplicateResourceException("Tag already exists: urgent"));

        mockMvc.perform(post("/api/tags")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"urgent\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void create_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("alice", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void list_authenticated_returnsPage() throws Exception {
        when(tagService.list(null, null))
                .thenReturn(new PageResponse<>(List.of(new TagResponse(5L, "urgent", "#ff0000")), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/tags")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("alice", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("urgent"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
