package io.github.huseyinbabal.taskhub.user;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.huseyinbabal.taskhub.common.DuplicateResourceException;
import io.github.huseyinbabal.taskhub.common.InvalidCredentialsException;
import io.github.huseyinbabal.taskhub.security.JwtService;
import io.github.huseyinbabal.taskhub.user.dto.AuthResponse;
import io.github.huseyinbabal.taskhub.user.dto.LoginRequest;
import io.github.huseyinbabal.taskhub.user.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the auth service (SPEC §Session 2). Registration persists a
 * USER with an encoded password and returns a JWT; duplicates conflict; login
 * verifies the password and rejects bad credentials generically.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    JwtService jwtService;

    // Real encoder — cheap and gives higher confidence than a stubbed one.
    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    AuthService authService;

    void init() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void register_persistsUserWithEncodedPassword_andReturnsToken() {
        init();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken("alice", List.of("USER"))).thenReturn("signed-jwt");

        AuthResponse response = authService.register(
                new RegisterRequest("alice@example.com", "alice", "password123"));

        assertThat(response.token()).isEqualTo("signed-jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.roles()).containsExactly("USER");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRoles()).containsExactly(Role.USER);
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("password123"); // encoded, not plaintext
        assertThat(passwordEncoder.matches("password123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void register_duplicateUsername_throwsConflict() {
        init();
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice@example.com", "alice", "password123")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        init();
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice@example.com", "alice", "password123")))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_validCredentials_returnsToken() {
        init();
        User stored = new User("alice@example.com", "alice",
                passwordEncoder.encode("password123"), Set.of(Role.USER));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(stored));
        when(jwtService.generateToken("alice", List.of("USER"))).thenReturn("signed-jwt");

        AuthResponse response = authService.login(new LoginRequest("alice", "password123"));

        assertThat(response.token()).isEqualTo("signed-jwt");
        assertThat(response.username()).isEqualTo("alice");
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        init();
        User stored = new User("alice@example.com", "alice",
                passwordEncoder.encode("password123"), Set.of(Role.USER));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_unknownUser_throwsInvalidCredentials() {
        init();
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
