package dev.workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WorkflowRepository extends JpaRepository<WorkflowRun,UUID> {
    List<WorkflowRun> findByTaskIdOrderByCreatedAtDesc(UUID taskId);
}
