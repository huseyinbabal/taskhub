package io.github.huseyinbabal.taskhub.project;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.huseyinbabal.taskhub.common.AccessDeniedException;
import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.common.ResourceNotFoundException;
import io.github.huseyinbabal.taskhub.project.dto.ProjectRequest;
import io.github.huseyinbabal.taskhub.project.dto.ProjectResponse;
import io.github.huseyinbabal.taskhub.security.CurrentUserProvider;
import io.github.huseyinbabal.taskhub.user.Role;
import io.github.huseyinbabal.taskhub.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for project CRUD + ownership (SPEC §Session 2 acceptance #2). A USER
 * only reaches their own projects; an ADMIN reaches all; missing projects 404 and
 * foreign projects 403.
 */
@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    ProjectRepository projectRepository;
    @Mock
    CurrentUserProvider currentUserProvider;

    ProjectService projectService;

    private User alice;
    private User bob;
    private User admin;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, new ProjectMapper(), currentUserProvider);
        alice = userWithId(1L, "alice", Role.USER);
        bob = userWithId(2L, "bob", Role.USER);
        admin = userWithId(9L, "admin", Role.ADMIN);
    }

    private User userWithId(Long id, String username, Role role) {
        User user = new User(username + "@example.com", username, "hash", Set.of(role));
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Project projectOwnedBy(Long id, User owner) {
        Project project = new Project("Website", "Marketing site", owner);
        ReflectionTestUtils.setField(project, "id", id);
        return project;
    }

    @Test
    void create_savesProjectOwnedByCurrentUser() {
        when(currentUserProvider.require()).thenReturn(alice);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse response = projectService.create(new ProjectRequest("Website", "Marketing site"));

        assertThat(response.name()).isEqualTo("Website");
        assertThat(response.ownerUsername()).isEqualTo("alice");
    }

    @Test
    void get_ownProject_returnsIt() {
        when(currentUserProvider.require()).thenReturn(alice);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(projectOwnedBy(10L, alice)));

        assertThat(projectService.get(10L).id()).isEqualTo(10L);
    }

    @Test
    void get_foreignProject_throwsAccessDenied() {
        when(currentUserProvider.require()).thenReturn(alice);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(projectOwnedBy(10L, bob)));

        assertThatThrownBy(() -> projectService.get(10L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void get_missingProject_throwsNotFound() {
        // require() is never reached — the missing project 404s first.
        when(projectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.get(404L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void get_adminReachesForeignProject() {
        when(currentUserProvider.require()).thenReturn(admin);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(projectOwnedBy(10L, bob)));

        assertThat(projectService.get(10L).id()).isEqualTo(10L);
    }

    @Test
    void update_ownProject_appliesChanges() {
        when(currentUserProvider.require()).thenReturn(alice);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(projectOwnedBy(10L, alice)));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse response = projectService.update(10L, new ProjectRequest("Renamed", "New desc"));

        assertThat(response.name()).isEqualTo("Renamed");
        assertThat(response.description()).isEqualTo("New desc");
    }

    @Test
    void update_foreignProject_throwsAccessDenied() {
        when(currentUserProvider.require()).thenReturn(alice);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(projectOwnedBy(10L, bob)));

        assertThatThrownBy(() -> projectService.update(10L, new ProjectRequest("x", null)))
                .isInstanceOf(AccessDeniedException.class);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void delete_ownProject_deletes() {
        Project project = projectOwnedBy(10L, alice);
        when(currentUserProvider.require()).thenReturn(alice);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));

        projectService.delete(10L);

        verify(projectRepository).delete(project);
    }

    @Test
    void list_asUser_returnsOwnProjectsPageWithMetadata() {
        when(currentUserProvider.require()).thenReturn(alice);
        Page<Project> page = new PageImpl<>(List.of(projectOwnedBy(10L, alice)));
        when(projectRepository.findByOwnerId(eq(1L), any(Pageable.class))).thenReturn(page);

        PageResponse<ProjectResponse> response = projectService.list(0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        verify(projectRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void list_asAdmin_returnsAllProjects() {
        when(currentUserProvider.require()).thenReturn(admin);
        Page<Project> page = new PageImpl<>(List.of(projectOwnedBy(10L, alice), projectOwnedBy(11L, bob)));
        when(projectRepository.findAll(any(Pageable.class))).thenReturn(page);

        PageResponse<ProjectResponse> response = projectService.list(0, 20);

        assertThat(response.content()).hasSize(2);
    }
}
