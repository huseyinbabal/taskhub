package io.github.huseyinbabal.taskhub.user;

import java.util.List;
import java.util.Set;

import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Unit test for the user listing query (SPEC §Session 2). */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, new UserMapper());
    }

    @Test
    void list_mapsUsersToResponsesWithoutPassword() {
        User alice = new User("alice@example.com", "alice", "secret-hash", Set.of(Role.USER));
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(alice)));

        PageResponse<UserResponse> response = userService.list(0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).username()).isEqualTo("alice");
        assertThat(response.content().get(0).roles()).containsExactly("USER");
    }
}
