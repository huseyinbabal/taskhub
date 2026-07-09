package io.github.huseyinbabal.taskhub.security;

import java.util.List;

import io.github.huseyinbabal.taskhub.user.Role;
import io.github.huseyinbabal.taskhub.user.User;
import io.github.huseyinbabal.taskhub.user.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads users for credential-based authentication (the {@code /api/auth/login}
 * flow). Maps persisted {@link Role}s to {@code ROLE_}-prefixed authorities so
 * they align with the JWT filter and {@code @PreAuthorize("hasRole(...)")}.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .build();
    }
}
