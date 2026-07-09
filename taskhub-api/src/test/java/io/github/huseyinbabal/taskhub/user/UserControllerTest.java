package io.github.huseyinbabal.taskhub.user;

import java.time.Instant;
import java.util.List;

import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.config.CorsConfig;
import io.github.huseyinbabal.taskhub.config.SecurityConfig;
import io.github.huseyinbabal.taskhub.user.dto.UserResponse;
import io.github.huseyinbabal.taskhub.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the ADMIN-only user listing (SPEC §Session 2 acceptance #1):
 * unauthenticated → 401, USER → 403 (method security), ADMIN → 200.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, CorsConfig.class})
@TestPropertySource(properties = "taskhub.jwt.secret=" + TestTokens.DEV_SECRET)
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserService userService;

    @Test
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_asUser_returns403() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("alice", "USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_asAdmin_returns200WithPage() throws Exception {
        when(userService.list(any(), any())).thenReturn(new PageResponse<>(
                List.of(new UserResponse(1L, "alice@example.com", "alice", List.of("USER"),
                        Instant.parse("2026-07-09T10:00:00Z"))),
                0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer("admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("alice"))
                .andExpect(jsonPath("$.content[0].roles[0]").value("USER"));
    }
}
