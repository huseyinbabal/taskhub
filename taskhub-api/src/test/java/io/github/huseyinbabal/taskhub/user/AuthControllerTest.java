package io.github.huseyinbabal.taskhub.user;

import java.util.List;

import io.github.huseyinbabal.taskhub.common.InvalidCredentialsException;
import io.github.huseyinbabal.taskhub.config.CorsConfig;
import io.github.huseyinbabal.taskhub.config.SecurityConfig;
import io.github.huseyinbabal.taskhub.user.dto.AuthResponse;
import io.github.huseyinbabal.taskhub.user.dto.LoginRequest;
import io.github.huseyinbabal.taskhub.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the public auth endpoints with the real security chain wired in
 * (SPEC §Session 2 acceptance #3). {@code /api/auth/**} is reachable without a
 * token; invalid bodies produce field-level 400s; bad credentials surface as 401.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @Test
    void register_validBody_returns201WithBearerToken() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(AuthResponse.bearer("signed-jwt", "alice", List.of("USER")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","username":"alice","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("signed-jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    void register_invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","username":"a","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void login_validCredentials_returns200() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(AuthResponse.bearer("signed-jwt", "alice", List.of("USER")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed-jwt"));
    }

    @Test
    void login_badCredentials_returns401() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
