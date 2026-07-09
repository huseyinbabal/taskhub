package io.github.huseyinbabal.taskhub.project;

import io.github.huseyinbabal.taskhub.common.AccessDeniedException;
import io.github.huseyinbabal.taskhub.common.PageRequests;
import io.github.huseyinbabal.taskhub.common.PageResponse;
import io.github.huseyinbabal.taskhub.common.ResourceNotFoundException;
import io.github.huseyinbabal.taskhub.project.dto.ProjectRequest;
import io.github.huseyinbabal.taskhub.project.dto.ProjectResponse;
import io.github.huseyinbabal.taskhub.security.CurrentUserProvider;
import io.github.huseyinbabal.taskhub.user.Role;
import io.github.huseyinbabal.taskhub.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project CRUD with ownership enforcement (SPEC §Session 2). A {@code USER} may
 * only see/modify projects they own; an {@code ADMIN} may act on any project.
 */
@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final CurrentUserProvider currentUserProvider;

    public ProjectService(ProjectRepository projectRepository, ProjectMapper projectMapper,
                          CurrentUserProvider currentUserProvider) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        User owner = currentUserProvider.require();
        Project project = new Project(request.name(), request.description(), owner);
        return projectMapper.toResponse(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> list(Integer page, Integer size) {
        User user = currentUserProvider.require();
        PageRequest pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Project> projects = isAdmin(user)
                ? projectRepository.findAll(pageable)
                : projectRepository.findByOwnerId(user.getId(), pageable);
        return PageResponse.from(projects.map(projectMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long id) {
        return projectMapper.toResponse(requireAccessible(id));
    }

    @Transactional
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = requireAccessible(id);
        project.setName(request.name());
        project.setDescription(request.description());
        return projectMapper.toResponse(projectRepository.save(project));
    }

    @Transactional
    public void delete(Long id) {
        projectRepository.delete(requireAccessible(id));
    }

    /** Loads a project the current user is allowed to touch, else 404 (missing) / 403 (foreign). */
    private Project requireAccessible(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + id + " not found"));
        User user = currentUserProvider.require();
        if (!isAdmin(user) && !project.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have access to project " + id);
        }
        return project;
    }

    private boolean isAdmin(User user) {
        return user.getRoles().contains(Role.ADMIN);
    }
}
