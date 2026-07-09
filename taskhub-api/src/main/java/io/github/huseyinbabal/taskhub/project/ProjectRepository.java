package io.github.huseyinbabal.taskhub.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Page<Project> findByOwnerId(Long ownerId, Pageable pageable);
}
