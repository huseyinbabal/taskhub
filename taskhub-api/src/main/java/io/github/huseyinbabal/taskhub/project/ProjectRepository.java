package io.github.huseyinbabal.taskhub.project;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Fetch the owner alongside the projects so mapping to DTO does not trigger
    // an N+1 on list endpoints (SPEC Boundaries: never introduce N+1 on lists).
    @EntityGraph(attributePaths = "owner")
    Page<Project> findByOwnerId(Long ownerId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Project> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "owner")
    Optional<Project> findById(Long id);
}
