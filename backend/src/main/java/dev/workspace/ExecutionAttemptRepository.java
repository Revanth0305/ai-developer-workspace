package dev.workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ExecutionAttemptRepository extends JpaRepository<ExecutionAttempt,UUID> {
    List<ExecutionAttempt> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
