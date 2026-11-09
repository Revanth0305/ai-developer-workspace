package dev.workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface DeploymentRepository extends JpaRepository<Deployment,UUID> {
    List<Deployment> findByExecutionIdOrderByCreatedAtDesc(UUID executionId);
    List<Deployment> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
