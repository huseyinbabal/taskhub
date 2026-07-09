package io.github.huseyinbabal.taskhub.task;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    // Fetch project + assignee so mapping does not trigger an N+1 on the list
    // endpoint (SPEC Boundaries: never introduce N+1 on lists).
    @EntityGraph(attributePaths = {"project", "assignee"})
    Page<Task> findByProjectId(Long projectId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"project", "assignee"})
    Optional<Task> findById(Long id);
}
