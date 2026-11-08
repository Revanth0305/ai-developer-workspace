package dev.workspace;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_tasks")
public class Task {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable = false) public UUID projectId;
    @Column(nullable = false, length = 12000) public String instruction;
    @Column(nullable = false) public String status = "DRAFT";
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();
    protected Task() {}
    Task(UUID projectId, String instruction) {
        this.projectId = projectId;
        this.instruction = instruction;
    }
}
