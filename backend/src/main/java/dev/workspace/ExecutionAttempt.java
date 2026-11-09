package dev.workspace;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="workspace_execution_attempts")
public class ExecutionAttempt {
    @Id public UUID id=UUID.randomUUID();
    @Column(nullable=false) public UUID taskId;
    @Column(nullable=false) public UUID planAttemptId;
    public UUID repairOf;
    public String status="GENERATING";
    public String baseCommit="";
    public String sourceFingerprint="";
    public String candidateFingerprint="";
    public String reviewHash="";
    public String imageId="";
    public String profile="NODE24_TEST";
    public int checkRuns;
    public Integer exitCode;
    public Integer testCount;
    public boolean cleanupPending;
    @Lob public String contextPreview="";
    @Lob public String selectedPaths="[]";
    @Lob public String proposal="";
    @Lob public String diff="";
    @Lob public String logs="";
    @Column(length=2000) public String summary="";
    @Column(length=1000) public String error="";
    public Instant createdAt=Instant.now();
    public Instant updatedAt=Instant.now();
    protected ExecutionAttempt() {}
    ExecutionAttempt(UUID taskId,UUID planAttemptId) { this.taskId=taskId;this.planAttemptId=planAttemptId; }
}
