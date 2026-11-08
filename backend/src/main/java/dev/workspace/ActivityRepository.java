package dev.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {
    List<Activity> findTop50ByOrderByCreatedAtDesc();
    List<Activity> findTop50ByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
