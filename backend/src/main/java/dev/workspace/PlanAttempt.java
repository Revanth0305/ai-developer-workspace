package dev.workspace;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_plan_attempts")
public class PlanAttempt {
    @Id public UUID id = UUID.randomUUID();
    @Column(nullable = false) public UUID taskId;
    public String status = "RUNNING";
    @Lob public String contextPreview;
    @Lob public String result = "";
    @Column(length = 1000) public String error = "";
    public String threadId;
    public Instant startedAt = Instant.now();
    public Instant finishedAt;
    protected PlanAttempt() {}
    PlanAttempt(UUID taskId, String contextPreview) { this.taskId = taskId; this.contextPreview = contextPreview; }
}
