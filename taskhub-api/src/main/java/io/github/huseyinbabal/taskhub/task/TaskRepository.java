package io.github.huseyinbabal.taskhub.task;

import java.util.Optional;

import jakarta.persistence.QueryHint;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // Fetch project + assignee so mapping does not trigger an N+1 on the list
    // endpoint (SPEC Boundaries: never introduce N+1 on lists).
    // Query-cached (spec/hibernate-l2-cache-hazelcast.md): a project's task list is read
    // repeatedly between changes; the cached query (ids) is invalidated automatically when
    // any task row is written, so a mutation cannot serve a stale list.
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
    @EntityGraph(attributePaths = {"project", "assignee"})
    Page<Task> findByProjectId(Long projectId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"project", "assignee"})
    Optional<Task> findById(Long id);
}
