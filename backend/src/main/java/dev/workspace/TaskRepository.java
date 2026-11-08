package dev.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findAllByOrderByCreatedAtDesc();
    List<Task> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
