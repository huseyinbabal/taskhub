package io.github.huseyinbabal.taskhub.project;

import java.util.Optional;

import jakarta.persistence.QueryHint;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Fetch the owner alongside the projects so mapping to DTO does not trigger
    // an N+1 on list endpoints (SPEC Boundaries: never introduce N+1 on lists).
    // Query-cached (spec/hibernate-l2-cache-hazelcast.md): a user's own-project listing is
    // the per-user landing query; the result set (ids) is served from Hazelcast and stays
    // consistent — a project insert/update to this owner bumps the table's update timestamp
    // and invalidates the cached query.
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    @EntityGraph(attributePaths = "owner")
    Page<Project> findByOwnerId(Long ownerId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "owner")
    Page<Project> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "owner")
    Optional<Project> findById(Long id);
}
