package dev.workspace;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="workspace_workflows")
public class WorkflowRun {
    @Id public UUID id=UUID.randomUUID();
    public UUID taskId,executionId;
    public String status="PROPOSING",imageId="",contextHash="";
    @Lob public String reviewedContext="",selection="",events="";
    public int maxCandidates,candidates;
    public boolean cleanupPending;
    @Column(length=1000) public String error="";
    public Instant createdAt=Instant.now(),updatedAt=Instant.now(),deadline;
}
