package io.github.huseyinbabal.taskhub.user;

import io.github.huseyinbabal.taskhub.common.PageRequests;
import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.user.dto.UserResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User administration queries (SPEC §Session 2). Authorization (ADMIN) is enforced at the controller. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Integer page, Integer size) {
        PageRequest pageable = PageRequests.of(page, size, Sort.by("username"));
        return PageResponse.from(userRepository.findAll(pageable).map(userMapper::toResponse));
    }
}
