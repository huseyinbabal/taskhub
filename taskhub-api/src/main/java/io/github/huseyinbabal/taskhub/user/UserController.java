package io.github.huseyinbabal.taskhub.user;

import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.user.dto.UserResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User administration (SPEC §Session 2): listing users is ADMIN-only, enforced
 * with method security. A {@code USER} calling this gets 403.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserResponse> list(@RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return userService.list(page, size);
    }
}
