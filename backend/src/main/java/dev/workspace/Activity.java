package dev.workspace;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_activity")
public class Activity {
    @Id public UUID id = UUID.randomUUID();
    public UUID projectId;
    public UUID taskId;
    @Column(nullable = false, length = 2000) public String message;
    public Instant createdAt = Instant.now();
    protected Activity() {}
    Activity(UUID projectId, UUID taskId, String message) {
        this.projectId = projectId;
        this.taskId = taskId;
        this.message = message;
    }
}
