package dev.workspace;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="workspace_deployments")
public class Deployment {
    @Id public UUID id=UUID.randomUUID();
    public UUID executionId,projectId,taskId;
    public String profile="LOCAL_STATIC_NODE24",status="REVIEW",terminalStatus="";
    public String commitId="",artifactHash="",imageId="",reviewHash="",url="";
    public String directory="public";
    @Lob public String manifest="",logs="";
    @Column(length=1000) public String error="";
    public boolean cleanupPending;
    public Instant createdAt=Instant.now(),updatedAt=Instant.now(),expiresAt;
    public Integer port;
}
