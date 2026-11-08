package dev.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PlanAttemptRepository extends JpaRepository<PlanAttempt, UUID> {
    List<PlanAttempt> findByTaskIdOrderByStartedAtDesc(UUID taskId);
    List<PlanAttempt> findByStatus(String status);
}
