package io.github.huseyinbabal.taskhub.user;

import java.util.List;
import java.util.Set;

import io.github.huseyinbabal.taskhub.common.DuplicateResourceException;
import io.github.huseyinbabal.taskhub.common.InvalidCredentialsException;
import io.github.huseyinbabal.taskhub.security.JwtService;
import io.github.huseyinbabal.taskhub.user.dto.AuthResponse;
import io.github.huseyinbabal.taskhub.user.dto.LoginRequest;
import io.github.huseyinbabal.taskhub.user.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login (SPEC §Session 2). New users are created with the
 * {@code USER} role and a BCrypt-hashed password; both flows return a signed JWT.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username already taken: " + request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email already registered: " + request.email());
        }
        User user = new User(
                request.email(),
                request.username(),
                passwordEncoder.encode(request.password()),
                Set.of(Role.USER));
        User saved = userRepository.save(user);
        return toAuthResponse(saved);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                // Same generic error whether the user is missing or the password is wrong.
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }
        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::name)
                .sorted()
                .toList();
        String token = jwtService.generateToken(user.getUsername(), roles);
        return AuthResponse.bearer(token, user.getUsername(), roles);
    }
}
